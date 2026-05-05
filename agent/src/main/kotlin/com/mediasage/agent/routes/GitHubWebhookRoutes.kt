package com.mediasage.agent.routes

import com.mediasage.agent.service.AgentLaunchService
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
import java.util.logging.Logger
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
    val comment: GitHubComment? = null
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

private data class WebhookContext(val ticketKey: String, val prNumber: Int, val branchRef: String, val commentBody: String)

private val log = Logger.getLogger("GitHubWebhookRoutes")
private val ticketKeyRegex = Regex("[A-Z]+-\\d+")
private val webhookJson = Json { ignoreUnknownKeys = true }
fun Route.githubWebhookRoutes(webhookSecret: String) {
    val agentService by inject<AgentLaunchService>()
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

private suspend fun handleGitHubEvent(
    eventType: String,
    rawBody: ByteArray,
    agentService: AgentLaunchService,
    jiraLabelChecker: JiraLabelChecker
) {
    when (eventType) {
        "pull_request_review" -> {
            val context = parseReviewContext(rawBody) ?: return
            log.info("GitHub review submitted: ticketKey=${context.ticketKey} PR#${context.prNumber}")
            if (jiraLabelChecker.isAutonomous(context.ticketKey)) {
                agentService.launchForPrReview(context.ticketKey, context.prNumber, context.branchRef, context.commentBody)
            }
        }
        "pull_request_review_comment" -> {
            val prNumber = parseInlineCommentPrNumber(rawBody) ?: return
            log.info("GitHub inline comment on PR#$prNumber — posting quick reply")
            agentService.postInlineCommentReply(prNumber)
        }
    }
}

private fun parseReviewContext(rawBody: ByteArray): WebhookContext? {
    val payload = webhookJson.decodeFromString<GitHubWebhookPayload>(rawBody.decodeToString())
    val reviewBody = payload.review?.body.orEmpty()
    val ticketKey = ticketKeyRegex.find(payload.pullRequest.head.ref)?.value

    return ticketKey
        ?.takeIf { payload.action == "submitted" }
        ?.takeIf { payload.review?.state == "changes_requested" }
        ?.takeIf { !reviewBody.startsWith("🤖 **Agent:**") }
        ?.let { WebhookContext(it, payload.pullRequest.number, payload.pullRequest.head.ref, reviewBody) }
}

private fun parseInlineCommentPrNumber(rawBody: ByteArray): Int? {
    val payload = webhookJson.decodeFromString<GitHubWebhookPayload>(rawBody.decodeToString())
    val commentBody = payload.comment?.body.orEmpty()
    return payload.pullRequest.number
        .takeIf { payload.action == "created" }
        ?.takeIf { !commentBody.startsWith("🤖 **Agent:**") }
}

private fun validateSignature(secret: String, body: ByteArray, signature: String): Boolean {
    val expected = "sha256=${computeHmacSha256(secret, body)}"
    return MessageDigest.isEqual(expected.toByteArray(Charsets.UTF_8), signature.toByteArray(Charsets.UTF_8))
}

private fun computeHmacSha256(secret: String, data: ByteArray): String {
    val mac = Mac.getInstance("HmacSHA256")
    mac.init(SecretKeySpec(secret.toByteArray(Charsets.UTF_8), "HmacSHA256"))
    return mac.doFinal(data).joinToString("") { "%02x".format(it) }
}
