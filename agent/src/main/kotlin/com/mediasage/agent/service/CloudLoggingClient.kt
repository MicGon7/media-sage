package com.mediasage.agent.service

import com.google.auth.oauth2.GoogleCredentials
import com.mediasage.agent.db.WorkerMetrics
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.delay
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.double
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import org.slf4j.LoggerFactory
import java.io.ByteArrayInputStream

/**
 * Reads worker efficiency metrics from Cloud Logging after a Cloud Run Job execution completes.
 *
 * Claude Code writes a single `result` event as the last line of its stream-json output.
 * Cloud Run captures container stdout into Cloud Logging, so that line is queryable by
 * execution ID. This client fetches it, parses the token/cost fields, and returns them
 * as [WorkerMetrics].
 *
 * Returns null (never throws) if logs are unavailable or the result line cannot be found —
 * the caller degrades gracefully by storing a COMPLETED row with null metric columns.
 *
 * ## Pub/Sub compatibility (MS-201)
 * This client is a pure function of executionName → WorkerMetrics?. When LRO polling is
 * replaced by Pub/Sub events, the call site in [CloudRunJobsClient.handleDone] moves to a
 * message handler, but this client is unchanged.
 */
class CloudLoggingClient(
    private val httpClient: HttpClient,
    private val projectId: String,
    private val credentialsJson: String,
    // Injectable for testing — production callers omit this and the default uses GCP credentials.
    private val tokenProvider: (() -> String)? = null
) {
    private val log = LoggerFactory.getLogger(CloudLoggingClient::class.java)
    private val json = Json { ignoreUnknownKeys = true }

    private val credentials: GoogleCredentials by lazy {
        GoogleCredentials
            .fromStream(ByteArrayInputStream(credentialsJson.toByteArray()))
            .createScoped("https://www.googleapis.com/auth/logging.read")
    }

    /**
     * Fetches the Claude Code `result` event for [executionName] from Cloud Logging and
     * parses it into [WorkerMetrics]. Retries up to [MAX_ATTEMPTS] times to account for
     * Cloud Logging ingestion latency (typically 5–15 seconds after container exit).
     *
     * @param executionName Full Cloud Run execution resource name, e.g.
     *   `projects/p/locations/r/jobs/j/executions/j-xxxxx`
     */
    suspend fun fetchMetrics(executionName: String): WorkerMetrics? {
        val executionId = executionName.substringAfterLast("/")
        repeat(MAX_ATTEMPTS) { attempt ->
            delay(if (attempt == 0) INITIAL_DELAY_MS else RETRY_DELAY_MS)
            val metrics = tryFetch(executionId)
            if (metrics != null) return metrics
            log.info("[metrics] Attempt ${attempt + 1}/$MAX_ATTEMPTS — result event not yet in logs for $executionId")
        }
        log.warn("[metrics] Result event not found in Cloud Logging after $MAX_ATTEMPTS attempts for $executionId")
        return null
    }

    private suspend fun tryFetch(executionId: String): WorkerMetrics? {
        val response = runCatching {
            httpClient.post("https://logging.googleapis.com/v2/entries:list") {
                header(HttpHeaders.Authorization, "Bearer ${accessToken()}")
                contentType(ContentType.Application.Json)
                setBody(listEntriesBody(executionId))
            }
        }.getOrElse {
            log.warn("[metrics] Cloud Logging request failed: ${it.message}")
            return null
        }

        if (!response.status.isSuccess()) {
            log.warn("[metrics] Cloud Logging returned ${response.status}")
            return null
        }

        return parseMetricsFromResponse(response.bodyAsText())
    }

    /**
     * Builds the Cloud Logging entries.list request body.
     * Filters to textPayload entries containing the Claude Code `result` event marker.
     */
    private fun listEntriesBody(executionId: String): String {
        val filter = """resource.type="cloud_run_job" """ +
            """resource.labels.execution_id="$executionId" """ +
            """textPayload=~"\"type\":\"result\""""
        val encodedFilter = json.encodeToString(JsonPrimitive.serializer(), JsonPrimitive(filter))
        return """{"resourceNames":["projects/$projectId"],"filter":$encodedFilter,"orderBy":"timestamp desc","pageSize":10}"""
    }

    /**
     * Walks the `entries` array from the Cloud Logging response and returns the first
     * entry whose `textPayload` is a valid Claude Code `result` event.
     */
    private fun parseMetricsFromResponse(responseBody: String): WorkerMetrics? {
        return runCatching {
            val entries = json.parseToJsonElement(responseBody).jsonObject["entries"]?.jsonArray
            entries?.firstNotNullOfOrNull { parseResultEntry(it.jsonObject) }
        }.getOrElse {
            log.warn("[metrics] Failed to parse Cloud Logging response: ${it.message}")
            null
        }
    }

    /**
     * Parses a single Cloud Logging entry. Returns [WorkerMetrics] if the entry's
     * `textPayload` is a Claude Code `result` event, null otherwise.
     */
    private fun parseResultEntry(entry: JsonObject): WorkerMetrics? {
        val text = entry["textPayload"]?.jsonPrimitive?.content
        val event = text?.let { runCatching { json.parseToJsonElement(it).jsonObject }.getOrNull() }
        val usage = event?.takeIf { it["type"]?.jsonPrimitive?.content == "result" }?.get("usage")?.jsonObject
        return usage?.let {
            WorkerMetrics(
                inputTokens = it["input_tokens"]?.jsonPrimitive?.int ?: 0,
                outputTokens = it["output_tokens"]?.jsonPrimitive?.int ?: 0,
                cacheReadTokens = it["cache_read_input_tokens"]?.jsonPrimitive?.int ?: 0,
                cacheCreationTokens = it["cache_creation_input_tokens"]?.jsonPrimitive?.int ?: 0,
                totalCostUsd = event["total_cost_usd"]?.jsonPrimitive?.double ?: 0.0,
                durationMs = event["duration_ms"]?.jsonPrimitive?.long ?: 0L,
                numTurns = event["num_turns"]?.jsonPrimitive?.int ?: 0
            )
        }
    }

    private fun accessToken(): String {
        if (tokenProvider != null) return tokenProvider.invoke()
        credentials.refreshIfExpired()
        return credentials.accessToken.tokenValue
    }

    private companion object {
        const val MAX_ATTEMPTS = 5
        const val INITIAL_DELAY_MS = 15_000L  // Cloud Logging ingestion is typically 5–15s
        const val RETRY_DELAY_MS = 10_000L
    }
}
