package com.mediasage.orchestrator

import com.mediasage.pipeline.core.JobCompletionEvent
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Wire-contract tests for the worker → orchestrator completion event (MS-386).
 *
 * The worker publishes [JobCompletionEvent] as base64 JSON on Pub/Sub; the orchestrator
 * decodes it in `PubSubWebhookRoutes`. These guard the `failedGate` field added in MS-386,
 * including backward compatibility with events published before the field existed.
 */
class JobCompletionEventTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `failedGate deserializes when the worker reports a gate on failure`() {
        val event = json.decodeFromString(
            JobCompletionEvent.serializer(),
            """{"ticketKey":"MS-1","executionName":"exec-1","status":"failure","failedGate":"detekt"}"""
        )
        assertEquals("failure", event.status)
        assertEquals("detekt", event.failedGate)
    }

    @Test
    fun `failedGate defaults to null when absent (success or pre-MS-386 worker)`() {
        val event = json.decodeFromString(
            JobCompletionEvent.serializer(),
            """{"ticketKey":"MS-1","executionName":"exec-1","status":"success"}"""
        )
        assertEquals("success", event.status)
        assertNull(event.failedGate)
    }
}
