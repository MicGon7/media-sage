package com.mediasage.agent.routes

import com.mediasage.agent.db.JobRegistry
import com.mediasage.agent.service.CloudRunJobsClient
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import java.util.Base64

private val log = LoggerFactory.getLogger("PubSubWebhookRoutes")
private val json = Json { ignoreUnknownKeys = true }

@Serializable
private data class PubSubMessage(
    @SerialName("data") val data: String,
    @SerialName("messageId") val messageId: String = "",
    @SerialName("publishTime") val publishTime: String = ""
)

@Serializable
private data class PubSubPushRequest(
    @SerialName("message") val message: PubSubMessage,
    @SerialName("subscription") val subscription: String = ""
)

@Serializable
private data class JobCompletionEvent(
    @SerialName("ticketKey") val ticketKey: String,
    @SerialName("executionName") val executionName: String,
    @SerialName("status") val status: String, // "success" or "failure"
    @SerialName("commentBody") val commentBody: String? = null
)

/**
 * Receives Pub/Sub push messages from the worker when a Cloud Run job execution completes.
 *
 * The worker publishes a [JobCompletionEvent] to the `cloud-run-job-completions` topic after
 * Claude Code exits. Pub/Sub delivers it here via HTTP push. The route acknowledges immediately
 * (HTTP 200) and processes the completion in a background coroutine so Pub/Sub does not
 * misinterpret a slow metrics fetch as a delivery failure and retry prematurely.
 *
 * Authentication: the push subscription URL includes `?token=<secret>` which is verified on
 * every delivery. Requests with a missing or wrong token are rejected with 401.
 */
fun Route.pubSubWebhookRoutes(
    webhookSecret: String,
    cloudRunJobsClient: CloudRunJobsClient,
    jobRegistry: JobRegistry,
    scope: CoroutineScope
) {
    post("/webhook/pubsub") {
        if (call.request.queryParameters["token"] != webhookSecret) {
            log.warn("Pub/Sub webhook: rejected — invalid or missing token")
            call.respond(HttpStatusCode.Unauthorized)
            return@post
        }
        val event = parsePushEvent(call) ?: run {
            call.respond(HttpStatusCode.BadRequest)
            return@post
        }
        log.info("[${event.ticketKey}] Pub/Sub completion event: status=${event.status}, execution=${event.executionName}")
        // Acknowledge immediately — Pub/Sub retries on non-2xx. Metrics fetch (~15s) runs in background.
        call.respond(HttpStatusCode.OK)
        scope.launch { processCompletion(event, jobRegistry, cloudRunJobsClient) }
    }
}

private suspend fun parsePushEvent(call: io.ktor.server.application.ApplicationCall): JobCompletionEvent? {
    val pushRequest = runCatching { call.receive<PubSubPushRequest>() }.getOrElse {
        log.warn("Pub/Sub webhook: failed to parse push envelope: ${it.message}")
        return null
    }
    return runCatching {
        val decoded = String(Base64.getDecoder().decode(pushRequest.message.data))
        json.decodeFromString(JobCompletionEvent.serializer(), decoded)
    }.getOrElse {
        log.warn("Pub/Sub webhook: failed to decode message data: ${it.message}")
        null
    }
}

private suspend fun processCompletion(
    event: JobCompletionEvent,
    jobRegistry: JobRegistry,
    cloudRunJobsClient: CloudRunJobsClient
) {
    val job = jobRegistry.findRunningByTicketKey(event.ticketKey)
    if (job == null) {
        log.warn("[${event.ticketKey}] Pub/Sub webhook: no RUNNING job found — may have already been processed")
        return
    }
    cloudRunJobsClient.onJobCompleted(
        jobId = job.jobId,
        ticketKey = event.ticketKey,
        executionName = event.executionName,
        succeeded = event.status == "success",
        commentBody = event.commentBody
    )
}
