package com.mediasage.agent

import com.mediasage.agent.service.BriefingContext
import com.mediasage.agent.service.HaikuBriefingService
import io.ktor.client.*
import io.ktor.client.engine.mock.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BriefingServiceTest {

    private fun buildClient(responseBody: String, status: HttpStatusCode = HttpStatusCode.OK): HttpClient =
        // Tests for HaikuBriefingService use runTest which is fine here because HaikuBriefingService
        // is tested in isolation — no AgentLaunchService coroutine scope involved.
        HttpClient(MockEngine { _ ->
            respond(
                responseBody,
                status,
                headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
            )
        }) {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true })
            }
        }

    private val successResponse = """
        {
          "content": [{ "type": "text", "text": "Fix the period at line 42 in AgentLauncher.kt." }],
          "id": "msg_01",
          "model": "claude-haiku-4-5-20251001",
          "role": "assistant",
          "stop_reason": "end_turn",
          "type": "message",
          "usage": { "input_tokens": 10, "output_tokens": 20 }
        }
    """.trimIndent()

    // ── BriefingContext subtype coverage ──────────────────────────────────────

    @Test
    fun `brief returns non-null for TicketWork context`() = runTest {
        val service = HaikuBriefingService(buildClient(successResponse), "https://api.test", "token")
        val result = service.brief(BriefingContext.TicketWork("MS-1", "Add a health endpoint"))
        assertNotNull(result)
        assertTrue(result.isNotBlank())
    }

    @Test
    fun `brief returns non-null for PrReview context`() = runTest {
        val service = HaikuBriefingService(buildClient(successResponse), "https://api.test", "token")
        val result = service.brief(
            BriefingContext.PrReview("MS-1", 42, "Add periods to KDoc", "- val foo: String")
        )
        assertNotNull(result)
    }

    @Test
    fun `brief returns non-null for CommentReview context`() = runTest {
        val service = HaikuBriefingService(buildClient(successResponse), "https://api.test", "token")
        val result = service.brief(BriefingContext.CommentReview("MS-1", 42, "Why is this a suspend fun?"))
        assertNotNull(result)
    }

    @Test
    fun `brief returns non-null for ConflictResolution context`() = runTest {
        val service = HaikuBriefingService(buildClient(successResponse), "https://api.test", "token")
        val result = service.brief(
            BriefingContext.ConflictResolution("MS-1", 42, "feature/MS-1-fix", "main")
        )
        assertNotNull(result)
    }

    // ── Failure / fallback behaviour ─────────────────────────────────────────

    @Test
    fun `brief returns null on HTTP error`() = runTest {
        val service = HaikuBriefingService(
            buildClient("""{"error":"unauthorized"}""", HttpStatusCode.Unauthorized),
            "https://api.test", "bad-token"
        )
        val result = service.brief(BriefingContext.TicketWork("MS-1", "content"))
        assertNull(result)
    }

    @Test
    fun `brief returns null when response content is empty`() = runTest {
        val emptyResponse = """{ "content": [], "id": "msg_01", "model": "m", "role": "assistant",
            "stop_reason": "end_turn", "type": "message", "usage": {"input_tokens":1,"output_tokens":0} }"""
        val service = HaikuBriefingService(buildClient(emptyResponse), "https://api.test", "token")
        val result = service.brief(BriefingContext.TicketWork("MS-1", "content"))
        assertNull(result)
    }

    // ── Diff truncation ───────────────────────────────────────────────────────

    @Test
    fun `PrReview diff is capped at 300 lines in the prompt sent to the API`() = runTest {
        var capturedBody = ""
        val client = HttpClient(MockEngine { request ->
            capturedBody = request.body.toByteArray().toString(Charsets.UTF_8)
            respond(successResponse, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
        }) {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        }
        val longDiff = (1..500).joinToString("\n") { "line $it" }
        val service = HaikuBriefingService(client, "https://api.test", "token")
        service.brief(BriefingContext.PrReview("MS-1", 1, "comment", longDiff))

        // The captured request body should not contain line 301+
        assertTrue("line 300" in capturedBody)
        assertTrue("line 301" !in capturedBody)
    }
}
