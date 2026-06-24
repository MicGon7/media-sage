package com.mediasage.analyst

import com.mediasage.analyst.scoring.ClaudeDecisionScorer
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ClaudeDecisionScorerTest {

    @Test
    fun retriesOnTransientFailureAndSucceeds() = runTest {
        var callCount = 0
        val engine = MockEngine {
            callCount++
            if (callCount < 3) {
                respond(
                    content = "Service Unavailable",
                    status = HttpStatusCode.ServiceUnavailable,
                    headers = headersOf("Content-Type", ContentType.Application.Json.toString()),
                )
            } else {
                respond(
                    content = SCORING_SUCCESS_RESPONSE,
                    status = HttpStatusCode.OK,
                    headers = headersOf("Content-Type", ContentType.Application.Json.toString()),
                )
            }
        }
        val scorer = ClaudeDecisionScorer(mockClient(engine), "test-token", "http://test")

        val scores = scorer.callClaudeWithRetry("some transcript")

        assertEquals(3, callCount, "Expected exactly 3 HTTP calls (2 failures + 1 success)")
        assertEquals(1, scores.size)
        assertEquals("tool_choice", scores[0].criterion)
        assertEquals(4, scores[0].score)
    }

    @Test
    fun throwsAfterAllAttemptsExhausted() = runTest {
        var callCount = 0
        val engine = MockEngine {
            callCount++
            respond(
                content = "Internal Server Error",
                status = HttpStatusCode.InternalServerError,
                headers = headersOf("Content-Type", ContentType.Application.Json.toString()),
            )
        }
        val scorer = ClaudeDecisionScorer(mockClient(engine), "test-token", "http://test")

        assertFailsWith<IllegalStateException> {
            scorer.callClaudeWithRetry("some transcript")
        }
        assertEquals(3, callCount, "Expected exactly 3 HTTP calls before giving up")
    }
}

private fun mockClient(engine: MockEngine) = HttpClient(engine) {
    install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
}

private val SCORING_SUCCESS_RESPONSE = """
{
  "id": "msg_test",
  "type": "message",
  "role": "assistant",
  "content": [
    {
      "type": "tool_use",
      "id": "toolu_test",
      "name": "record_scores",
      "input": {
        "scores": [
          {"criterion": "tool_choice", "score": 4, "rationale": "Good tool selection"}
        ]
      }
    }
  ],
  "model": "claude-sonnet-4-6",
  "stop_reason": "tool_use",
  "usage": {"input_tokens": 100, "output_tokens": 50}
}
""".trimIndent()
