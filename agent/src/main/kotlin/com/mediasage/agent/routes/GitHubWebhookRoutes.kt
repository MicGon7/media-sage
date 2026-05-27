package com.mediasage.agent.routes

import com.mediasage.agent.service.AgentLauncher
import com.mediasage.agent.service.JiraLabelChecker
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.koin.ktor.ext.inject
import java.security.MessageDigest
import org.slf4j.LoggerFactory
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

// ---- GitHub webhook payload DTOs ----

@Serializable
data class GitHubWebhookPayload(
    @SerialName("action")
    val action: String,
    @SerialName("sender")
    val sender: GitHubUser,
    @SerialName("pull_request")
    val pullRequest: GitHubPullRequest,
    @SerialName("review")
    val review: GitHubReview? = null,
    @SerialName("comment")
    val comment: GitHubComment? = null,
    @SerialName("reason")
    val reason: String? = null
)

@Serializable
data class GitHubUser(
    @SerialName("login")
    val login: String
)

@Serializable
data class GitHubPullRequest(
    @SerialName("number")
    val number: Int,
    @SerialName("head")
    val head: GitHubBranch
)

@Serializable
data class GitHubBranch(
    @SerialName("ref")
    val ref: String
)

@Serializable
data class GitHubReview(
    @SerialName("body")
    val body: String? = null,
    @SerialName("state")
    val state: String
)

@Serializable
data class GitHubComment(
    @SerialName("id")
    val id: Long,
    @SerialName("body")
    val body: String
)

private data class WebhookContext(
    val ticketKey: String,
    val prNumber: Int,
    val branchRef: String,
    val commentBody: String,
    val reviewState: String,
    val reviewerLogin: String
)

private data class DequeueContext(
    val ticketKey: String,
    val prNumber: Int,
    val branchRef: String
)

private val log = LoggerFactory.getLogger("GitHubWebhookRoutes")
private val ticketKeyRegex = Regex("[A-Z]+-\\d+")
private val webhookJson = Json { ignoreUnknownKeys = true }

/**
 * Registers the GitHub webhook route at `POST /webhook/github`.
 *
 * Accepts [pull_request], [pull_request_review], and [pull_request_review_comment] events from GitHub.
 * Validates the request signature using HMAC-SHA256 against [webhookSecret], then dispatches
 * to the appropriate handler based on the event type.
 *
 * Expected headers:
 * - `X-GitHub-Event`: event type (`pull_request`, `pull_request_review`, `pull_request_review_comment`)
 * - `X-Hub-Signature-256`: HMAC-SHA256 signature of the raw request body
 *
 * Responds `200 OK` on success, `400 Bad Request` if the event header is missing,
 * `401 Unauthorized` if the signature is missing or invalid.
 *
 * @param webhookSecret shared secret used to verify the GitHub webhook HMAC-SHA256 signature
 */
fun Route.githubWebhookRoutes(webhookSecret: String) {
    val agentService by inject<AgentLauncher>()
    val jiraLabelChecker by inject<JiraLabelChecker>()

    post("/webhook/github") {
        val eventType = call.request.header("X-GitHub-Event") ?: run {
            call.respond(HttpStatusCode.BadRequest)
            return@post
        }
        val rawBody = call.receive<ByteArray>()
        val signature = call.request.header("X-Hub-Signature-256") ?: run {
            call.respond(HttpStatusCode.Unauthorized)
            return@post
        }
        if (!validateSignature(webhookSecret, rawBody, signature)) {
            call.respond(HttpStatusCode.Unauthorized)
            return@post
        }
        handleGitHubEvent(eventType, rawBody, agentService, jiraLabelChecker)
        call.respond(HttpStatusCode.OK)
    }
}

/**
 * Dispatches a verified GitHub webhook event to the appropriate handler.
 *
 * Supported events:
 * - `pull_request` with `action: dequeued` and `reason: merge_conflict`: if the ticket is labeled
 *   `autonomous`, dispatches a conflict-resolution Cloud Run Job via [AgentLauncher.launchForConflictResolution].
 *   CI-failure and other non-conflict dequeue reasons are silently ignored.
 * - `pull_request_review`: if the ticket extracted from the branch ref is labeled `autonomous`
 *   in Jira, launches the agent via [AgentLauncher.launchForPrReview] for `changes_requested`
 *   reviews or [AgentLauncher.launchForCommentReview] for `commented` reviews. Ignores
 *   agent-authored reviews (body starts with "🤖 **Agent:**") and all other review states.
 * - `pull_request_review_comment`: calls [AgentLauncher.postInlineCommentReply] for the PR.
 *   Ignores agent-authored comments.
 */
private suspend fun handleGitHubEvent(
    eventType: String,
    rawBody: ByteArray,
    agentService: AgentLauncher,
    jiraLabelChecker: JiraLabelChecker
) {
    when (eventType) {
        "pull_request" -> handleDequeueEvent(rawBody, agentService, jiraLabelChecker)
        "pull_request_review" -> handleReviewEvent(rawBody, agentService, jiraLabelChecker)
        "pull_request_review_comment" -> {
            val prNumber = parseInlineCommentPrNumber(rawBody) ?: return
            log.info("GitHub inline comment on PR#$prNumber — posting quick reply")
            agentService.postInlineCommentReply(prNumber)
        }
    }
}

private suspend fun handleDequeueEvent(
    rawBody: ByteArray,
    agentService: AgentLauncher,
    jiraLabelChecker: JiraLabelChecker
) {
    val context = parseDequeueContext(rawBody) ?: return
    log.info("[${context.ticketKey}] GitHub PR#${context.prNumber} dequeued (merge_conflict) — checking autonomous label")
    if (jiraLabelChecker.isAutonomous(context.ticketKey)) {
        agentService.launchForConflictResolution(context.ticketKey, context.prNumber, context.branchRef)
    }
}

private suspend fun handleReviewEvent(
    rawBody: ByteArray,
    agentService: AgentLauncher,
    jiraLabelChecker: JiraLabelChecker
) {
    val context = parseReviewContext(rawBody) ?: return
    log.info("GitHub review submitted: ticketKey=${context.ticketKey} PR#${context.prNumber} state=${context.reviewState}")
    if (!jiraLabelChecker.isAutonomous(context.ticketKey)) return
    when (context.reviewState) {
        "changes_requested" -> agentService.launchForPrReview(
            context.ticketKey, context.prNumber, context.branchRef, context.commentBody, context.reviewerLogin
        )
        "commented" -> agentService.launchForCommentReview(
            context.ticketKey, context.prNumber, context.branchRef, context.commentBody
        )
    }
}

/**
 * Parses a `pull_request` webhook payload into a [DequeueContext].
 *
 * Returns `null` if:
 * - the action is not `dequeued`
 * - the reason is not `merge_conflict` (e.g. CI failure, queue cleared — ignored)
 * - the branch ref contains no Jira ticket key matching `[A-Z]+-\d+`
 */
private fun parseDequeueContext(rawBody: ByteArray): DequeueContext? {
    val payload = webhookJson.decodeFromString<GitHubWebhookPayload>(rawBody.decodeToString())
    if (payload.action != "dequeued") return null
    if (payload.reason != "merge_conflict") return null
    val ticketKey = ticketKeyRegex.find(payload.pullRequest.head.ref)?.value ?: return null
    return DequeueContext(ticketKey, payload.pullRequest.number, payload.pullRequest.head.ref)
}

/**
 * Parses a `pull_request_review` webhook payload into a [WebhookContext].
 *
 * Returns `null` if:
 * - the action is not `submitted`
 * - the review state is not `changes_requested` or `commented`
 * - the branch ref contains no Jira ticket key matching `[A-Z]+-\d+`
 * - the review body was authored by the agent (starts with "🤖 **Agent:**")
 */
private fun parseReviewContext(rawBody: ByteArray): WebhookContext? {
    val payload = webhookJson.decodeFromString<GitHubWebhookPayload>(rawBody.decodeToString())
    val reviewBody = payload.review?.body.orEmpty()
    val ticketKey = ticketKeyRegex.find(payload.pullRequest.head.ref)?.value
    val state = payload.review?.state?.lowercase() ?: return null

    return ticketKey
        ?.takeIf { payload.action == "submitted" }
        ?.takeIf { state == "changes_requested" || state == "commented" }
        ?.takeIf { !reviewBody.startsWith("🤖 **Agent:**") }
        ?.let { WebhookContext(it, payload.pullRequest.number, payload.pullRequest.head.ref, reviewBody, state, payload.sender.login) }
}

/**
 * Parses a `pull_request_review_comment` payload and returns the PR number.
 *
 * Returns `null` if the action is not `created` or if the comment was authored by the agent
 * (body starts with "🤖 **Agent:**").
 */
private fun parseInlineCommentPrNumber(rawBody: ByteArray): Int? {
    val payload = webhookJson.decodeFromString<GitHubWebhookPayload>(rawBody.decodeToString())
    val commentBody = payload.comment?.body.orEmpty()
    return payload.pullRequest.number
        .takeIf { payload.action == "created" }
        ?.takeIf { !commentBody.startsWith("🤖 **Agent:**") }
}

/**
 * Verifies the GitHub HMAC-SHA256 signature header against the raw request body.
 *
 * Uses constant-time comparison via [MessageDigest.isEqual] to prevent timing attacks.
 *
 * @param secret the webhook shared secret
 * @param body the raw request body bytes
 * @param signature the value of the `X-Hub-Signature-256` header (e.g. `sha256=abc123...`)
 */
private fun validateSignature(secret: String, body: ByteArray, signature: String): Boolean {
    val expected = "sha256=${computeHmacSha256(secret, body)}"
    return MessageDigest.isEqual(expected.toByteArray(Charsets.UTF_8), signature.toByteArray(Charsets.UTF_8))
}

private fun computeHmacSha256(secret: String, data: ByteArray): String {
    val mac = Mac.getInstance("HmacSHA256")
    mac.init(SecretKeySpec(secret.toByteArray(Charsets.UTF_8), "HmacSHA256"))
    return mac.doFinal(data).joinToString("") { "%02x".format(it) }
}
