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
     *
     * Filters by execution ID only — no payload-type constraint — because the Claude Code
     * `result` event arrives as `textPayload` (raw JSON string) or `jsonPayload` (structured
     * object) depending on the API gateway. `orderBy: timestamp desc` + `pageSize: 10`
     * ensures the result event (always the last thing written) is in the first page.
     */
    private fun listEntriesBody(executionId: String): String {
        val filter = """resource.type="cloud_run_job" resource.labels.execution_id="$executionId""""
        val encodedFilter = json.encodeToString(JsonPrimitive.serializer(), JsonPrimitive(filter))
        return """{"resourceNames":["projects/$projectId"],"filter":$encodedFilter,"orderBy":"timestamp desc","pageSize":10}"""
    }

    /**
     * Walks the `entries` array from the Cloud Logging response and returns the first
     * entry that is a Claude Code `result` event.
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
     * Parses a single Cloud Logging entry. Returns [WorkerMetrics] if the entry is a
     * Claude Code `result` event, null otherwise.
     *
     * Handles two payload shapes:
     * - `textPayload`: raw JSON string (direct Claude Code stdout, no API gateway)
     * - `jsonPayload`: structured object (API gateway re-serialises the stream-json line)
     *
     * When the result comes via `jsonPayload`, the top-level `usage` token counts are
     * zeroed out by the gateway. In that case we sum the per-model `modelUsage` entries
     * which use camelCase keys (`inputTokens`, `cacheReadInputTokens`, etc.).
     */
    private fun parseResultEntry(entry: JsonObject): WorkerMetrics? {
        val event = extractResultEvent(entry) ?: return null
        val tokenCounts = resolveTokenCounts(event)
        return WorkerMetrics(
            inputTokens = tokenCounts.inputTokens,
            outputTokens = tokenCounts.outputTokens,
            cacheReadTokens = tokenCounts.cacheReadTokens,
            cacheCreationTokens = tokenCounts.cacheCreationTokens,
            totalCostUsd = event["total_cost_usd"]?.jsonPrimitive?.double ?: 0.0,
            durationMs = event["duration_ms"]?.jsonPrimitive?.long ?: 0L,
            numTurns = event["num_turns"]?.jsonPrimitive?.int ?: 0
        )
    }

    /** Extracts the event [JsonObject] from a log entry, or null if it is not a `result` event. */
    private fun extractResultEvent(entry: JsonObject): JsonObject? {
        val event = entry["textPayload"]?.jsonPrimitive?.content
            ?.let { runCatching { json.parseToJsonElement(it).jsonObject }.getOrNull() }
            ?: entry["jsonPayload"]?.jsonObject
        return event?.takeIf { it["type"]?.jsonPrimitive?.content == "result" }
    }

    private data class TokenCounts(
        val inputTokens: Int,
        val outputTokens: Int,
        val cacheReadTokens: Int,
        val cacheCreationTokens: Int,
    )

    /**
     * Resolves token counts from the result event.
     *
     * Prefers top-level `usage` (snake_case) when non-zero. Falls back to summing
     * per-model `modelUsage` (camelCase) when the API gateway zeroes `usage`.
     */
    private fun resolveTokenCounts(event: JsonObject): TokenCounts {
        val usage = event["usage"]?.jsonObject
        val modelUsage = event["modelUsage"]?.jsonObject
        return TokenCounts(
            inputTokens = resolveToken(usage, "input_tokens", modelUsage, "inputTokens"),
            outputTokens = resolveToken(usage, "output_tokens", modelUsage, "outputTokens"),
            cacheReadTokens = resolveToken(usage, "cache_read_input_tokens", modelUsage, "cacheReadInputTokens"),
            cacheCreationTokens = resolveToken(usage, "cache_creation_input_tokens", modelUsage, "cacheCreationInputTokens"),
        )
    }

    /** Returns the snake_case [usageKey] value from [usage] if > 0, else sums [modelKey] across [modelUsage]. */
    private fun resolveToken(
        usage: JsonObject?,
        usageKey: String,
        modelUsage: JsonObject?,
        modelKey: String,
    ): Int = usage?.get(usageKey)?.jsonPrimitive?.int?.takeIf { it > 0 }
        ?: modelUsage?.values?.sumOf { it.jsonObject[modelKey]?.jsonPrimitive?.int ?: 0 }
        ?: 0

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
