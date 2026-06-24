package com.mediasage.analyst.routes

import com.mediasage.analyst.scoring.DecisionScorer
import com.mediasage.pipeline.core.JobCompletionEvent
import com.mediasage.pipeline.core.JobRegistry
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.launch
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import java.util.Base64

private val log = LoggerFactory.getLogger("PubSubCompletionRoutes")
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

/**
 * Receives Pub/Sub push messages on the Analyst's own subscription of the
 * `cloud-run-job-completions` topic.
 *
 * This is the reactive spine: every time a worker finishes, Pub/Sub delivers a
 * [JobCompletionEvent] here. The Analyst looks up the corresponding row in the shared `jobs`
 * table (written by the orchestrator via `:pipelineCore`) and records the outcome. It never
 * mutates the row — the orchestrator owns job-state transitions.
 *
 * After responding 200, the handler fires [DecisionScorer.score] in the application scope so
 * scoring never blocks delivery acknowledgement. Pub/Sub retries on any non-2xx, so a cold
 * start or transient blip is harmless.
 *
 * Authentication mirrors the orchestrator: the push URL carries `?token=<secret>`, verified on
 * every delivery. A missing or wrong token is rejected with 401.
 */
fun Route.pubSubCompletionRoutes(
    webhookSecret: String,
    jobRegistry: JobRegistry,
    decisionScorer: DecisionScorer,
) {
    post("/webhook/pubsub") {
        if (call.request.queryParameters["token"] != webhookSecret) {
            log.warn("Pub/Sub completion: rejected — invalid or missing token")
            call.respond(HttpStatusCode.Unauthorized)
            return@post
        }
        val event = parsePushEvent(call) ?: run {
            call.respond(HttpStatusCode.BadRequest)
            return@post
        }
        val job = jobRegistry.findLatestJob(event.ticketKey)
        if (job == null) {
            log.info("[${event.ticketKey}] completion received (status=${event.status}) — no job row found")
        } else {
            log.info(
                "[${event.ticketKey}] completion recorded: event=${event.status}, " +
                    "dbStatus=${job.status}, execution=${event.executionName}"
            )
            call.application.launch { decisionScorer.score(job.jobId) }
        }
        call.respond(HttpStatusCode.OK)
    }
}

private suspend fun parsePushEvent(call: ApplicationCall): JobCompletionEvent? {
    val pushRequest = runCatching { call.receive<PubSubPushRequest>() }.getOrElse {
        log.warn("Pub/Sub completion: failed to parse push envelope: ${it.message}")
        return null
    }
    return runCatching {
        val decoded = String(Base64.getDecoder().decode(pushRequest.message.data))
        json.decodeFromString(JobCompletionEvent.serializer(), decoded)
    }.getOrElse {
        log.warn("Pub/Sub completion: failed to decode message data: ${it.message}")
        null
    }
}
