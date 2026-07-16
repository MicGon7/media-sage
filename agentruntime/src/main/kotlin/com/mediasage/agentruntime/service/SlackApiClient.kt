package com.mediasage.agentruntime.service

import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.http.isSuccess
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.slf4j.LoggerFactory

/**
 * Posts plain-text messages to a Slack incoming webhook.
 *
 * Wraps a single [HttpClient] pointed at [webhookUrl] (the value of the `SLACK_WEBHOOK_URL`
 * secret). When [webhookUrl] is blank the feature is not configured, so [send] is a safe
 * no-op — the completion pipeline runs unchanged in environments without Slack wired up.
 *
 * Failures are logged at WARN and swallowed: a Slack outage must never fail job processing.
 *
 * @param httpClient Ktor HTTP client used for the webhook POST.
 * @param webhookUrl Slack incoming-webhook URL, or blank to disable notifications.
 */
class SlackApiClient(
    private val httpClient: HttpClient,
    private val webhookUrl: String,
) {

    private val log = LoggerFactory.getLogger(SlackApiClient::class.java)

    /**
     * Posts [text] as the `text` field of a Slack incoming-webhook payload. No-op when the
     * webhook URL is blank. Never throws — HTTP and serialization errors are logged and swallowed.
     */
    suspend fun send(text: String) {
        if (webhookUrl.isBlank()) {
            log.debug("Slack webhook URL not configured — skipping notification")
            return
        }
        try {
            val payload = Json.encodeToString(JsonObject.serializer(), buildJsonObject { put("text", text) })
            val response = httpClient.post(webhookUrl) {
                contentType(ContentType.Application.Json)
                setBody(payload)
            }
            if (!response.status.isSuccess()) {
                log.warn("Slack webhook returned ${response.status}")
            }
        } catch (e: Exception) {
            log.warn("Failed to post Slack notification: ${e.message}")
        }
    }
}
