package com.mediasage.server.routes

import com.mediasage.server.service.AgentLaunchService
import com.mediasage.server.service.JiraLabelChecker
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.koin.ktor.ext.inject
import java.security.MessageDigest
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

private data class WebhookContext(val ticketKey: String, val prNumber: Int, val commentBody: String)

private val ticketKeyRegex = Regex("[A-Z]+-\\d+")
private val webhookJson = Json { ignoreUnknownKeys = true }
private val relevantEventActions = mapOf(
    "pull_request_review" to "submitted",
    "pull_request_review_comment" to "created"
)

fun Route.githubWebhookRoutes(webhookSecret: String, botLogin: String) {
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

        val context = parseWebhookContext(eventType, rawBody, botLogin)
        if (context != null && jiraLabelChecker.isAutonomous(context.ticketKey)) {
            agentService.launchForPrReview(context.ticketKey, context.prNumber, context.commentBody)
        }

        call.respond(HttpStatusCode.OK)
    }
}

private fun parseWebhookContext(eventType: String, rawBody: ByteArray, botLogin: String): WebhookContext? {
    val expectedAction = relevantEventActions[eventType] ?: return null
    val payload = webhookJson.decodeFromString<GitHubWebhookPayload>(rawBody.decodeToString())
    val commentBody = extractCommentBody(eventType, payload)
    val ticketKey = ticketKeyRegex.find(payload.pullRequest.head.ref)?.value

    return ticketKey
        ?.takeIf { payload.action == expectedAction }
        ?.takeIf { payload.sender.login != botLogin }
        ?.takeIf { commentBody.isNotBlank() && !commentBody.startsWith("🤖 **Agent:**") }
        ?.takeIf { eventType != "pull_request_review" || payload.review?.state != "approved" }
        ?.let { WebhookContext(it, payload.pullRequest.number, commentBody) }
}

private fun extractCommentBody(eventType: String, payload: GitHubWebhookPayload): String =
    when (eventType) {
        "pull_request_review" -> payload.review?.body.orEmpty()
        "pull_request_review_comment" -> payload.comment?.body.orEmpty()
        else -> ""
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
