package com.mediasage.server

import com.mediasage.server.service.ClaudeApiException
import com.mediasage.server.service.ClaudeApiService
import com.mediasage.server.service.QuoteCandidate
import io.ktor.client.*
import io.ktor.client.engine.mock.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ClaudeApiServiceTest {

    private fun createMockClient(responseBody: String, status: HttpStatusCode = HttpStatusCode.OK): HttpClient {
        return HttpClient(MockEngine { _ ->
            respond(
                content = responseBody,
                status = status,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }) {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true })
            }
        }
    }

    private val sampleQuotes = listOf(
        QuoteCandidate(
            id = 1,
            figureName = "Augustine",
            text = "You have made us for yourself, O Lord, and our heart is restless until it rests in you.",
            source = "Confessions",
            themes = listOf("longing", "rest", "purpose")
        ),
        QuoteCandidate(
            id = 2,
            figureName = "Corrie ten Boom",
            text = "There is no pit so deep that God's love is not deeper still.",
            source = "The Hiding Place",
            themes = listOf("suffering", "hope", "love")
        )
    )

    @Test
    fun matchQuoteToHeadlineReturnsMatchResult() = runTest {
        val mockResponse = """
        {
            "id": "msg_123",
            "type": "message",
            "role": "assistant",
            "content": [
                {
                    "type": "text",
                    "text": "{\"selectedQuoteId\": 2, \"confidence\": 0.92, \"explanation\": \"Corrie ten Boom's quote speaks directly to finding hope in the depths of suffering.\", \"connectionThemes\": [\"suffering\", \"hope\", \"divine love\"]}"
                }
            ],
            "model": "claude-sonnet-4-6",
            "stop_reason": "end_turn",
            "usage": { "input_tokens": 200, "output_tokens": 50 }
        }
        """.trimIndent()

        val client = createMockClient(mockResponse)
        val service = ClaudeApiService(client, "test-api-key")

        val result = service.matchQuoteToHeadline(
            headlineTitle = "Earthquake devastates coastal city, thousands displaced",
            candidateQuotes = sampleQuotes
        )

        assertEquals(2, result.selectedQuoteId)
        assertEquals(0.92f, result.confidence)
        assertTrue(result.explanation.contains("Corrie ten Boom"))
        assertEquals(listOf("suffering", "hope", "divine love"), result.connectionThemes)
    }

    @Test
    fun matchQuoteHandlesMarkdownWrappedJson() = runTest {
        val mockResponse = """
        {
            "id": "msg_456",
            "type": "message",
            "role": "assistant",
            "content": [
                {
                    "type": "text",
                    "text": "```json\n{\"selectedQuoteId\": 1, \"confidence\": 0.85, \"explanation\": \"Augustine's restlessness speaks to the search for meaning.\", \"connectionThemes\": [\"purpose\", \"longing\"]}\n```"
                }
            ],
            "model": "claude-sonnet-4-6",
            "stop_reason": "end_turn",
            "usage": { "input_tokens": 200, "output_tokens": 40 }
        }
        """.trimIndent()

        val client = createMockClient(mockResponse)
        val service = ClaudeApiService(client, "test-api-key")

        val result = service.matchQuoteToHeadline(
            headlineTitle = "Youth mental health crisis deepens",
            candidateQuotes = sampleQuotes
        )

        assertEquals(1, result.selectedQuoteId)
        assertEquals(0.85f, result.confidence)
    }

    @Test
    fun matchQuoteThrowsOnApiError() = runTest {
        val errorResponse = """
        {
            "type": "error",
            "error": { "type": "authentication_error", "message": "Invalid API key" }
        }
        """.trimIndent()

        val client = createMockClient(errorResponse, HttpStatusCode.Unauthorized)
        val service = ClaudeApiService(client, "bad-key")

        val exception = assertFailsWith<ClaudeApiException> {
            service.matchQuoteToHeadline(
                headlineTitle = "Test headline",
                candidateQuotes = sampleQuotes
            )
        }

        assertEquals(401, exception.statusCode)
    }
}
