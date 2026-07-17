package com.mediasage.agentruntime

import com.mediasage.pipeline.core.JobCompletionEvent
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Wire-contract tests for the worker → orchestrator completion event.
 *
 * The worker publishes [JobCompletionEvent] as base64 JSON on Pub/Sub; the orchestrator
 * decodes it in `PubSubWebhookRoutes`. These guard the metric, job-type, and review-signal
 * fields, including backward compatibility with events published before each field existed.
 */
class JobCompletionEventTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `metrics fields round-trip when worker embeds them in payload`() {
        val event = json.decodeFromString(
            JobCompletionEvent.serializer(),
            """{"ticketKey":"MS-1","executionName":"exec-1","status":"success",
              "numTurns":12,"totalCostUsd":0.0423,"durationMs":47000,
              "modelVersion":"claude-sonnet-4-6","inputTokens":15000,
              "outputTokens":3000,"cacheReadTokens":90000,"cacheCreationTokens":1000}"""
        )
        assertEquals(12, event.numTurns)
        assertEquals(0.0423, event.totalCostUsd)
        assertEquals(47000L, event.durationMs)
        assertEquals("claude-sonnet-4-6", event.modelVersion)
        assertEquals(15000, event.inputTokens)
        assertEquals(3000, event.outputTokens)
        assertEquals(90000, event.cacheReadTokens)
        assertEquals(1000, event.cacheCreationTokens)
    }

    @Test
    fun `jobType and reviewCommentCount deserialize when the worker reports them`() {
        val event = json.decodeFromString(
            JobCompletionEvent.serializer(),
            """{"ticketKey":"QUALITY-314","executionName":"exec-1","status":"success",
              "jobType":"pr-quality-work","jiraTicketKey":"MS-314",
              "prNumber":314,"reviewCommentCount":3}"""
        )
        assertEquals("pr-quality-work", event.jobType)
        assertEquals(3, event.reviewCommentCount)
        assertEquals(314, event.prNumber)
    }

    @Test
    fun `reviewCommentCount of zero deserializes as clean signal (not null)`() {
        val event = json.decodeFromString(
            JobCompletionEvent.serializer(),
            """{"ticketKey":"QUALITY-1","executionName":"exec-1","status":"success",
              "jobType":"pr-quality-work","reviewCommentCount":0}"""
        )
        assertEquals(0, event.reviewCommentCount)
    }

    @Test
    fun `jobType and reviewCommentCount default to null when absent (older worker)`() {
        val event = json.decodeFromString(
            JobCompletionEvent.serializer(),
            """{"ticketKey":"MS-1","executionName":"exec-1","status":"success"}"""
        )
        assertNull(event.jobType)
        assertNull(event.reviewCommentCount)
    }

    @Test
    fun `metrics fields default to null when absent (older worker)`() {
        val event = json.decodeFromString(
            JobCompletionEvent.serializer(),
            """{"ticketKey":"MS-1","executionName":"exec-1","status":"success"}"""
        )
        assertNull(event.numTurns)
        assertNull(event.totalCostUsd)
        assertNull(event.durationMs)
        assertNull(event.modelVersion)
        assertNull(event.inputTokens)
        assertNull(event.outputTokens)
        assertNull(event.cacheReadTokens)
        assertNull(event.cacheCreationTokens)
    }

}
