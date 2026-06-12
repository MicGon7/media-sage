package com.mediasage.orchestrator

import com.mediasage.orchestrator.service.CloudLoggingClient
import io.ktor.client.*
import io.ktor.client.engine.mock.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Unit tests for [CloudLoggingClient] log parsing.
 *
 * All tests use a [MockEngine] so no real network or GCP credentials are needed.
 * The focus is on the parseResultEntry path: verifying correct field extraction
 * from the Cloud Logging response and graceful null returns on bad data.
 */
class CloudLoggingClientTest {

    private val resultEventPayload = """
        {"type":"result","subtype":"success","is_error":false,"duration_ms":1234567,
         "duration_api_ms":800000,"num_turns":42,"result":"done","stop_reason":"end_turn",
         "session_id":"abc","total_cost_usd":1.2345,"usage":{"input_tokens":10000,
         "cache_creation_input_tokens":500,"cache_read_input_tokens":8000,
         "output_tokens":2000},"modelUsage":{}}
    """.trimIndent().replace("\n", "")

    private fun mockClient(responseBody: String, status: HttpStatusCode = HttpStatusCode.OK): HttpClient {
        return HttpClient(MockEngine) {
            engine {
                addHandler {
                    respond(responseBody, status, headersOf(HttpHeaders.ContentType, "application/json"))
                }
            }
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        }
    }

    private fun client(httpClient: HttpClient) =
        CloudLoggingClient(
            httpClient = httpClient,
            projectId = "test-project",
            credentialsJson = "",
            tokenProvider = { "fake-token" }
        )

    // ── Happy path ────────────────────────────────────────────────────────────

    @Test
    fun `fetchMetrics parses result event correctly`() = runTest {
        val loggingResponse = """
            {"entries":[{"textPayload":${ Json.encodeToString(kotlinx.serialization.json.JsonPrimitive.serializer(), kotlinx.serialization.json.JsonPrimitive(resultEventPayload)) }}]}
        """.trimIndent()

        val metrics = client(mockClient(loggingResponse)).fetchMetrics(
            "projects/p/locations/r/jobs/j/executions/j-abc123"
        )

        assertEquals(10000, metrics?.inputTokens)
        assertEquals(2000, metrics?.outputTokens)
        assertEquals(8000, metrics?.cacheReadTokens)
        assertEquals(500, metrics?.cacheCreationTokens)
        assertEquals(1.2345, metrics?.totalCostUsd)
        assertEquals(1234567L, metrics?.durationMs)
        assertEquals(42, metrics?.numTurns)
        // modelUsage is empty in this fixture → no model can be resolved
        assertEquals(null, metrics?.modelVersion)
    }

    // ── No entries in response ────────────────────────────────────────────────

    @Test
    fun `fetchMetrics returns null when entries array is empty`() = runTest {
        val metrics = client(mockClient("""{"entries":[]}""")).fetchMetrics(
            "projects/p/locations/r/jobs/j/executions/j-empty"
        )
        assertNull(metrics)
    }

    // ── Entry is not a result event ───────────────────────────────────────────

    @Test
    fun `fetchMetrics returns null when no entry has type result`() = runTest {
        val assistantPayload = """{"type":"assistant","message":{"content":"hello"}}"""
        val loggingResponse = """{"entries":[{"textPayload":${
            Json.encodeToString(kotlinx.serialization.json.JsonPrimitive.serializer(), kotlinx.serialization.json.JsonPrimitive(assistantPayload))
        }}]}"""

        val metrics = client(mockClient(loggingResponse)).fetchMetrics(
            "projects/p/locations/r/jobs/j/executions/j-noResult"
        )
        assertNull(metrics)
    }

    // ── Cloud Logging returns non-2xx ─────────────────────────────────────────

    @Test
    fun `fetchMetrics returns null on non-2xx response`() = runTest {
        val metrics = client(mockClient("{}", HttpStatusCode.Forbidden)).fetchMetrics(
            "projects/p/locations/r/jobs/j/executions/j-forbidden"
        )
        assertNull(metrics)
    }

    // ── Malformed JSON payload ────────────────────────────────────────────────

    @Test
    fun `fetchMetrics returns null when textPayload is not valid JSON`() = runTest {
        val loggingResponse = """{"entries":[{"textPayload":"not-json-at-all"}]}"""
        val metrics = client(mockClient(loggingResponse)).fetchMetrics(
            "projects/p/locations/r/jobs/j/executions/j-bad"
        )
        assertNull(metrics)
    }

    // ── jsonPayload path (API gateway) ────────────────────────────────────────

    @Test
    fun `fetchMetrics parses result event from jsonPayload with modelUsage fallback`() = runTest {
        // Mirrors the real MS-180 log entry: jsonPayload with zeroed usage and
        // per-model token counts in modelUsage (camelCase keys).
        val loggingResponse = """
            {"entries":[{"jsonPayload":{
              "type":"result","total_cost_usd":2.0007,"duration_ms":1385655,"num_turns":47,
              "usage":{"input_tokens":0,"output_tokens":0,"cache_read_input_tokens":0,"cache_creation_input_tokens":0},
              "modelUsage":{
                "claude-sonnet-4":{"inputTokens":9977,"outputTokens":38185,"cacheReadInputTokens":4653492,"cacheCreationInputTokens":0},
                "claude-haiku-4-5":{"inputTokens":1832,"outputTokens":26,"cacheReadInputTokens":0,"cacheCreationInputTokens":0}
              }
            }}]}
        """.trimIndent()

        val metrics = client(mockClient(loggingResponse)).fetchMetrics(
            "projects/p/locations/r/jobs/j/executions/j-gateway"
        )

        assertEquals(9977 + 1832, metrics?.inputTokens)
        assertEquals(38185 + 26, metrics?.outputTokens)
        assertEquals(4653492, metrics?.cacheReadTokens)
        assertEquals(0, metrics?.cacheCreationTokens)
        assertEquals(2.0007, metrics?.totalCostUsd)
        assertEquals(1385655L, metrics?.durationMs)
        assertEquals(47, metrics?.numTurns)
        // modelVersion is the first modelUsage key (the model that ran the session)
        assertEquals("claude-sonnet-4", metrics?.modelVersion)
    }
}
