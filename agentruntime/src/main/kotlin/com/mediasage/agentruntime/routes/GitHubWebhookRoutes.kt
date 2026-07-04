package com.mediasage.agentruntime.routes

import com.mediasage.agentruntime.service.AgentLauncher
import com.mediasage.agentruntime.service.TicketSystemClient
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
    val head: GitHubBranch,
    @SerialName("base")
    val base: GitHubBranch = GitHubBranch("main"),
    @SerialName("user")
    val user: GitHubUser,
    @SerialName("merged")
    val merged: Boolean = false,
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

private data class WebhookContext(
    val prNumber: Int,
    val reviewState: String,
)

private data class DequeueContext(
    val prNumber: Int,
)

private data class MergeContext(
    val prNumber: Int,
    val ticketKey: String,
)

private val ticketKeyRegex = Regex("[A-Z]+-\\d+")

private val log = LoggerFactory.getLogger("GitHubWebhookRoutes")
private val webhookJson = Json { ignoreUnknownKeys = true }

/**
 * Registers the GitHub webhook route at `POST /webhook/github`.
 *
 * Accepts [pull_request] and [pull_request_review] events from GitHub.
 * Validates the request signature using HMAC-SHA256 against [webhookSecret], then dispatches
 * to the appropriate handler based on the event type.
 *
 * Only acts on events for PRs authored by [botLogin] — PRs opened by humans are silently ignored.
 * This replaces the previous Jira `autonomous` label check, making the gate portable across projects
 * and removing a live Jira API call from the webhook hot path.
 *
 * Expected headers:
 * - `X-GitHub-Event`: event type (`pull_request`, `pull_request_review`, `pull_request_review_comment`)
 * - `X-Hub-Signature-256`: HMAC-SHA256 signature of the raw request body
 *
 * Responds `200 OK` on success (including unrecognised event types), `400 Bad Request` if the
 * event header is missing, `401 Unauthorized` if the signature is missing or invalid.
 *
 * @param webhookSecret shared secret used to verify the GitHub webhook HMAC-SHA256 signature
 * @param botLogin GitHub login of the bot account (e.g. `media-sage-worker[bot]`). Only PRs authored
 *   by this identity trigger agent dispatch.
 */
fun Route.githubWebhookRoutes(webhookSecret: String, botLogin: String) {
    val agentService by inject<AgentLauncher>()
    val ticketClient by inject<TicketSystemClient>()

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
        handleGitHubEvent(eventType, rawBody, agentService, ticketClient, botLogin)
        call.respond(HttpStatusCode.OK)
    }
}

/**
 * Dispatches a verified GitHub webhook event to the appropriate handler.
 *
 * Supported events:
 * - `pull_request` with `action: dequeued` and `reason: merge_conflict`: if the PR was authored
 *   by [botLogin], dispatches a conflict-resolution Cloud Run Job via [AgentLauncher.launchForConflictResolution].
 *   CI-failure and other non-conflict dequeue reasons are silently ignored.
 * - `pull_request_review` with `state: changes_requested`: if the PR was authored by [botLogin],
 *   launches the agent via [AgentLauncher.launchForPrReview]. Ignores agent-authored reviews
 *   (body starts with "🤖 **Agent:**") and all other review states.
 * - All other event types: silently ignored, returns `200 OK`.
 */
private suspend fun handleGitHubEvent(
    eventType: String,
    rawBody: ByteArray,
    agentService: AgentLauncher,
    ticketClient: TicketSystemClient,
    botLogin: String,
) {
    when (eventType) {
        "pull_request" -> {
            handleDequeueEvent(rawBody, agentService, botLogin)
            handleMergeEvent(rawBody, ticketClient, agentService)
        }
        "pull_request_review" -> handleReviewEvent(rawBody, agentService, botLogin)
    }
}

private suspend fun handleDequeueEvent(
    rawBody: ByteArray,
    agentService: AgentLauncher,
    botLogin: String
) {
    val context = parseDequeueContext(rawBody, botLogin) ?: return
    log.info("PR#${context.prNumber} dequeued (merge_conflict) — bot-authored, dispatching conflict resolver")
    agentService.launchForConflictResolution(context.prNumber)
}

private suspend fun handleReviewEvent(
    rawBody: ByteArray,
    agentService: AgentLauncher,
    botLogin: String
) {
    val context = parseReviewContext(rawBody, botLogin) ?: return
    log.info("PR#${context.prNumber} review submitted (state=${context.reviewState}) — dispatching PR review worker")
    agentService.launchForPrReview(context.prNumber)
}

/**
 * Parses a `pull_request` webhook payload into a [DequeueContext].
 *
 * Returns `null` if:
 * - the action is not `dequeued`
 * - the reason is not `merge_conflict` (e.g. CI failure, queue cleared — ignored)
 * - the PR was not authored by [botLogin]
 */
private fun parseDequeueContext(rawBody: ByteArray, botLogin: String): DequeueContext? {
    val payload = webhookJson.decodeFromString<GitHubWebhookPayload>(rawBody.decodeToString())
    if (payload.action != "dequeued" || payload.reason != "merge_conflict") return null
    if (payload.pullRequest.user.login != botLogin) {
        log.info("PR#${payload.pullRequest.number} dequeued but not bot-authored (${payload.pullRequest.user.login}), ignoring")
        return null
    }
    return DequeueContext(payload.pullRequest.number)
}

/**
 * Parses a `pull_request_review` webhook payload into a [WebhookContext].
 *
 * Returns `null` if:
 * - the action is not `submitted`
 * - the review state is not `changes_requested`
 * - the PR was not authored by [botLogin]
 * - the review was submitted by [botLogin] (prevents feedback loops)
 * - the review body was authored by the agent (starts with "🤖 **Agent:**")
 */
private fun parseReviewContext(rawBody: ByteArray, botLogin: String): WebhookContext? {
    val payload = webhookJson.decodeFromString<GitHubWebhookPayload>(rawBody.decodeToString())
    val reviewBody = payload.review?.body.orEmpty()
    val state = payload.review?.state?.lowercase() ?: return null
    val prNumber = payload.pullRequest.number

    if (payload.pullRequest.user.login != botLogin) {
        log.info("PR#$prNumber review submitted but not bot-authored (${payload.pullRequest.user.login}), ignoring")
        return null
    }
    if (payload.sender.login == botLogin) {
        log.info("PR#$prNumber review submitted by bot — ignoring to prevent feedback loop")
        return null
    }

    val valid = payload.action == "submitted" && state == "changes_requested" && !reviewBody.startsWith("🤖 **Agent:**")
    return if (valid) WebhookContext(prNumber, state) else null
}

/**
 * Handles a merged PR event (`pull_request`, `closed`, `merged: true`).
 *
 * Extracts the Jira ticket key from the branch name (e.g. `feature/MS-520-...` → `MS-520`),
 * queries [TicketSystemClient] for tickets newly unblocked by that merge, then dispatches
 * each via [AgentLauncher.launchForUnblockedTicket]. No-op if the PR was not merged, the
 * branch has no recognisable ticket key, or no tickets are unblocked.
 */
private suspend fun handleMergeEvent(
    rawBody: ByteArray,
    ticketClient: TicketSystemClient,
    agentService: AgentLauncher,
) {
    val context = parseMergeContext(rawBody) ?: return
    val unblocked = ticketClient.getNewlyUnblockedTickets(context.ticketKey)
    if (unblocked.isEmpty()) return
    log.info("PR#${context.prNumber} merged (${context.ticketKey}) — ${unblocked.size} ticket(s) unblocked")
    unblocked.forEach { ticket ->
        log.info("Dispatching $ticket unblocked by ${context.ticketKey}")
        agentService.launchForUnblockedTicket(ticket, context.ticketKey)
    }
}

/**
 * Parses a `pull_request` webhook payload into a [MergeContext].
 *
 * Returns `null` if:
 * - the action is not `closed` or the PR was not merged
 * - the branch name contains no recognisable `MS-NNN` ticket key
 */
private fun parseMergeContext(rawBody: ByteArray): MergeContext? {
    val payload = webhookJson.decodeFromString<GitHubWebhookPayload>(rawBody.decodeToString())
    if (payload.action != "closed" || !payload.pullRequest.merged) return null
    val branchRef = payload.pullRequest.head.ref
    val ticketKey = ticketKeyRegex.find(branchRef)?.value ?: run {
        log.info("PR#${payload.pullRequest.number} merged but '$branchRef' has no ticket key — ignoring")
        return null
    }
    return MergeContext(payload.pullRequest.number, ticketKey)
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
