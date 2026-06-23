package com.mediasage.analyst

import com.mediasage.analyst.plugins.configureContentNegotiation
import com.mediasage.analyst.plugins.configureStatusPages
import com.mediasage.analyst.routes.pubSubCompletionRoutes
import com.mediasage.pipeline.core.JobRegistry
import com.mediasage.pipeline.core.JobRow
import com.mediasage.pipeline.core.JobStatus
import com.mediasage.pipeline.core.WorkerMetrics
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import java.util.Base64
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals

private const val TEST_TOKEN = "test-pubsub-secret"

class PubSubCompletionRoutesTest {

    @Test
    fun missingTokenReturns401() = testPubSubApp {
        val response = client.post("/webhook/pubsub") {
            contentType(ContentType.Application.Json)
            setBody(pushEnvelope(completionEventJson("MS-42")))
        }
        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun wrongTokenReturns401() = testPubSubApp {
        val response = client.post("/webhook/pubsub?token=nope") {
            contentType(ContentType.Application.Json)
            setBody(pushEnvelope(completionEventJson("MS-42")))
        }
        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun validTokenButUnparseableBodyReturns400() = testPubSubApp {
        val response = client.post("/webhook/pubsub?token=$TEST_TOKEN") {
            contentType(ContentType.Application.Json)
            setBody("""{"not":"a push envelope"}""")
        }
        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun validCompletionLooksUpJobAndReturns200() {
        val registry = FakeJobRegistry(latest = jobRow("MS-42", JobStatus.COMPLETED))
        testPubSubApp(registry) {
            val response = client.post("/webhook/pubsub?token=$TEST_TOKEN") {
                contentType(ContentType.Application.Json)
                setBody(pushEnvelope(completionEventJson("MS-42")))
            }
            assertEquals(HttpStatusCode.OK, response.status)
            assertEquals("MS-42", registry.lastLookupTicketKey, "Analyst must look up the job row for the event's ticket")
        }
    }

    @Test
    fun completionWithNoMatchingJobStillReturns200() {
        val registry = FakeJobRegistry(latest = null)
        testPubSubApp(registry) {
            val response = client.post("/webhook/pubsub?token=$TEST_TOKEN") {
                contentType(ContentType.Application.Json)
                setBody(pushEnvelope(completionEventJson("MS-99")))
            }
            assertEquals(HttpStatusCode.OK, response.status)
            assertEquals("MS-99", registry.lastLookupTicketKey)
        }
    }
}

// ---- Payload builders ----

private fun completionEventJson(ticketKey: String) =
    """{"ticketKey":"$ticketKey","executionName":"media-sage-agent-worker-abc12","status":"success"}"""

private fun pushEnvelope(eventJson: String): String {
    val data = Base64.getEncoder().encodeToString(eventJson.toByteArray(Charsets.UTF_8))
    return """{"message":{"data":"$data","messageId":"1"},"subscription":"projects/x/subscriptions/y"}"""
}

private fun jobRow(ticketKey: String, status: JobStatus) =
    JobRow(jobId = UUID.randomUUID(), ticketKey = ticketKey, status = status, executionName = "exec")

// ---- Test harness ----

private fun testPubSubApp(
    registry: JobRegistry = FakeJobRegistry(),
    block: suspend ApplicationTestBuilder.() -> Unit
) = testApplication {
    application {
        configureContentNegotiation()
        configureStatusPages()
        routing { pubSubCompletionRoutes(TEST_TOKEN, registry) }
    }
    block()
}

private class FakeJobRegistry(private val latest: JobRow? = null) : JobRegistry {
    var lastLookupTicketKey: String? = null

    override suspend fun findLatestJob(ticketKey: String): JobRow? {
        lastLookupTicketKey = ticketKey
        return latest
    }

    override suspend fun shouldDispatch(ticketKey: String): Boolean = true
    override suspend fun insert(ticketKey: String, payload: String): UUID = UUID.randomUUID()
    override suspend fun markRunning(jobId: UUID, executionName: String) = Unit
    override suspend fun markCompleted(jobId: UUID, metrics: WorkerMetrics?, envStartupMs: Long?) = Unit
    override suspend fun markFailed(jobId: UUID, failedGate: String?, modelVersion: String?) = Unit
    override suspend fun markInterrupted(jobId: UUID) = Unit
    override suspend fun findRunningJobs(): List<JobRow> = emptyList()
    override suspend fun findRunningByTicketKey(ticketKey: String): JobRow? = null
}
