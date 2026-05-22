package com.mediasage.agent.routes

import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.slf4j.LoggerFactory
import java.util.Base64

// ---- Pub/Sub push subscription payload DTOs ----

@Serializable
data class PubSubPushPayload(
    @SerialName("message")
    val message: PubSubMessage,
    @SerialName("subscription")
    val subscription: String
)

@Serializable
data class PubSubMessage(
    @SerialName("data")
    val data: String? = null,
    @SerialName("messageId")
    val messageId: String,
    @SerialName("publishTime")
    val publishTime: String,
    @SerialName("attributes")
    val attributes: Map<String, String> = emptyMap()
)

private val log = LoggerFactory.getLogger("PubSubWebhookRoutes")

/**
 * Google Cloud Pub/Sub push subscription endpoint. Receives job completion notifications
 * published by Cloud Run when a worker execution finishes, decodes the base64-encoded message
 * payload, and logs the event. Returns HTTP 200 to acknowledge receipt so the message is not
 * redelivered by Pub/Sub.
 */
fun Route.pubSubWebhookRoutes() {
    post("/webhook/pubsub") {
        val payload = call.receive<PubSubPushPayload>()
        val decoded = payload.message.data?.let {
            Base64.getDecoder().decode(it).decodeToString()
        }
        log.info("Pub/Sub message received: id=${payload.message.messageId} data=$decoded")
        call.respond(HttpStatusCode.OK)
    }
}
