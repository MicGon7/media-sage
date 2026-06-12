package com.mediasage.orchestrator.service

import com.google.auth.oauth2.GoogleCredentials
import com.mediasage.pipeline.core.WorkerMetrics
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
import java.time.Instant

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
     * @return Parsed [WorkerMetrics] on success, or null if the result event was not found
     *   in Cloud Logging after [MAX_ATTEMPTS] retries or if the response could not be parsed.
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
                setBody(listEntriesBody(executionId, orderBy = "timestamp desc", pageSize = 10))
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
     * Fetches the timestamp of the FIRST log entry emitted for [executionName] — the worker
     * container's first stdout, written before the git clone. Subtracting the job's dispatch
     * time (`started_at`) yields the environment startup cost: Cloud Run cold start + worker
     * image pull, the dominant overhead for short jobs (MS-399).
     *
     * Unlike [fetchMetrics] this needs no ingestion retry — by the time a job completes, its
     * first log line is minutes old and long since ingested. Returns null (never throws) when
     * logs are unavailable or the timestamp cannot be parsed; the caller records a null column.
     *
     * The query is bounded with `timestamp >= [since]` (MS-403). An ascending `entries:list`
     * with no lower bound scans the whole retention window oldest-first and routinely returns an
     * empty first page plus a continuation token; this single-page call would then see nothing
     * and return null. Bounding the scan to the job's lifetime puts the worker's first log line
     * on the first page. [since] is the dispatch time (`started_at`), which always precedes the
     * container's first log.
     *
     * @param executionName Full Cloud Run execution resource name.
     * @param since Lower bound for the log scan — the job's dispatch timestamp.
     * @return The first log entry's [Instant], or null if unavailable.
     */
    suspend fun fetchFirstLogTimestamp(executionName: String, since: Instant): Instant? {
        val executionId = executionName.substringAfterLast("/")
        val response = runCatching {
            httpClient.post("https://logging.googleapis.com/v2/entries:list") {
                header(HttpHeaders.Authorization, "Bearer ${accessToken()}")
                contentType(ContentType.Application.Json)
                setBody(listEntriesBody(executionId, orderBy = "timestamp asc", pageSize = 1, since = since))
            }
        }.getOrElse {
            log.warn("[env-startup] Cloud Logging request failed: ${it.message}")
            return null
        }
        if (!response.status.isSuccess()) {
            log.warn("[env-startup] Cloud Logging returned ${response.status}")
            return null
        }
        return parseFirstTimestamp(response.bodyAsText())
    }

    /** Extracts the `timestamp` of the first entry in a Cloud Logging response, or null. */
    private fun parseFirstTimestamp(responseBody: String): Instant? = runCatching {
        val ts = json.parseToJsonElement(responseBody).jsonObject["entries"]?.jsonArray
            ?.firstOrNull()?.jsonObject?.get("timestamp")?.jsonPrimitive?.content
        if (ts == null) {
            // Empty result — log explicitly so env startup never fails silently again (MS-403).
            log.warn("[env-startup] No first log entry returned for the execution — env startup not recorded")
            null
        } else {
            Instant.parse(ts)
        }
    }.getOrElse {
        log.warn("[env-startup] Failed to parse first log timestamp: ${it.message}")
        null
    }

    /**
     * Builds the Cloud Logging entries.list request body.
     *
     * Cloud Run Jobs does not expose the execution ID in `resource.labels` — it is stored
     * under `labels."run.googleapis.com/execution_name"` as the short execution name
     * (e.g. `media-sage-agent-worker-pvk42`). We extract that short name from the last
     * path segment of the full execution resource name passed by the caller.
     *
     * [orderBy] (`timestamp desc` + [pageSize] 10) puts the `result` event — always the last
     * line Claude Code writes — on the first page. `timestamp asc` + pageSize 1 instead yields
     * the first container log line, used for environment startup timing (MS-399).
     *
     * [since], when set, adds a `timestamp >= <since>` lower bound. Required for ascending
     * queries to avoid an empty first page from an unbounded oldest-first scan (MS-403).
     */
    private fun listEntriesBody(executionId: String, orderBy: String, pageSize: Int, since: Instant? = null): String {
        val timeBound = since?.let { " timestamp>=\"$it\"" } ?: ""
        val filter =
            """resource.type="cloud_run_job" labels."run.googleapis.com/execution_name"="$executionId"$timeBound"""
        val encodedFilter = json.encodeToString(JsonPrimitive.serializer(), JsonPrimitive(filter))
        return """{"resourceNames":["projects/$projectId"],"filter":$encodedFilter,"orderBy":"$orderBy","pageSize":$pageSize}"""
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
            numTurns = event["num_turns"]?.jsonPrimitive?.int ?: 0,
            // The result event reports usage per model under `modelUsage`, keyed by model name.
            // A worker session runs a single model, so the first key is the model that ran.
            modelVersion = event["modelUsage"]?.jsonObject?.keys?.firstOrNull()
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

    /**
     * Returns a valid OAuth2 bearer token for the Cloud Logging API.
     *
     * Uses [tokenProvider] when set (test injection). In production, refreshes
     * [credentials] if expired and returns the current access token value.
     */
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
