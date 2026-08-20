package com.mediasage.agentruntime

import com.mediasage.pipeline.core.JobRegistry
import com.mediasage.pipeline.core.JobRow
import com.mediasage.pipeline.core.JobStatus
import com.mediasage.pipeline.core.WorkerMetrics
import com.mediasage.agentruntime.service.AgentLaunchService
import com.mediasage.agentruntime.service.CloudRunDispatch
import com.mediasage.agentruntime.service.JobDispatcher
import com.mediasage.agentruntime.service.JiraApiClient
import io.ktor.client.*
import io.ktor.client.engine.mock.*
import io.ktor.http.*
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Unit tests for Cloud Run dispatch dedup logic and INTERRUPTED recovery.
 *
 * Strategy: fake implementations of [JobRegistry], [JobDispatcher], and
 * [FakeJiraApiClient] replace all I/O. Tests use [runTest] + [advanceUntilIdle]
 * to drive the coroutines inside [AgentLaunchService] to completion before asserting.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class JobDispatchTest {

    // ── Fakes ─────────────────────────────────────────────────────────────────

    private class FakeJobRegistry(shouldDispatchResult: Boolean = true) : JobRegistry {
        var shouldDispatchResult = shouldDispatchResult
        var latestJob: JobRow? = null
        val inserted = mutableListOf<String>()          // ticket keys inserted
        val completed = mutableListOf<UUID>()
        val interrupted = mutableListOf<UUID>()
        val failed = mutableListOf<UUID>()
        var runningJobs: List<JobRow> = emptyList()

        override suspend fun shouldDispatch(ticketKey: String) = shouldDispatchResult
        override suspend fun findLatestJob(ticketKey: String) = latestJob
        override suspend fun insert(ticketKey: String, payload: String): UUID {
            inserted.add(ticketKey)
            return UUID.randomUUID()
        }
        override suspend fun markRunning(jobId: UUID, executionName: String) = Unit
        override suspend fun markCompleted(jobId: UUID, metrics: WorkerMetrics?) { completed.add(jobId) }
        override suspend fun markFailed(jobId: UUID, modelVersion: String?) { failed.add(jobId) }
        override suspend fun markInterrupted(jobId: UUID) { interrupted.add(jobId) }
        override suspend fun findRunningJobs() = runningJobs
        override suspend fun findRunningByTicketKey(ticketKey: String): JobRow? =
            runningJobs.firstOrNull { it.ticketKey == ticketKey }
    }

    private class FakeJobDispatcher(
        private val recoverResult: Boolean = true,
        // Lets a test hold executeJob suspended mid-flight to simulate a genuinely concurrent
        // duplicate webhook delivery arriving while the first dispatch hasn't finished yet.
        private val beforeExecute: suspend () -> Unit = {},
    ) : JobDispatcher {
        val executions = mutableListOf<String>()
        val jobTypes = mutableListOf<String>()
        var lastIdentifiers: Map<String, String> = emptyMap()
        val recoveries = mutableListOf<String>()

        override suspend fun executeJob(
            jobId: UUID, ticketKey: String, jobType: String, identifiers: Map<String, String>, jobNameOverride: String?
        ): Boolean {
            beforeExecute()
            executions.add(ticketKey)
            jobTypes.add(jobType)
            lastIdentifiers = identifiers
            return true
        }
        override suspend fun recoverJob(jobId: UUID, ticketKey: String, executionName: String): Boolean {
            recoveries.add(executionName)
            return recoverResult
        }
    }

    /**
     * In-memory subclass of [JiraApiClient] for unit tests.
     * Overrides all public methods with no-IO implementations so tests are not
     * sensitive to coroutine dispatcher timing from real HTTP calls.
     */
    private class FakeJiraApiClient(
        private val statusResponse: String? = null,
        val comments: MutableList<String> = mutableListOf(),
        val transitions: MutableList<String> = mutableListOf(),
    ) : JiraApiClient(
        httpClient = HttpClient(MockEngine { respond("", HttpStatusCode.OK) }),
        cloudId = "test",
        email = "test@test.com",
        apiToken = "token",
    ) {
        override suspend fun getTicketStatus(ticketKey: String) = statusResponse
        override suspend fun getTicketContent(ticketKey: String): String? = null
        override suspend fun addComment(ticketKey: String, body: String) {
            comments.add(ticketKey)
        }
        override suspend fun transitionToInProgress(ticketKey: String) {
            transitions.add(ticketKey)
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun cloudRunService(
        registry: FakeJobRegistry,
        dispatcher: FakeJobDispatcher,
        jiraApiClient: JiraApiClient? = null,
        scope: TestScope,
    ) = AgentLaunchService(
        scope = scope,
        cloudRun = CloudRunDispatch(dispatcher, registry),
        jiraApiClient = jiraApiClient,
    )

    // ── Dedup: RUNNING ────────────────────────────────────────────────────────

    @Test
    fun `duplicate webhook for RUNNING job is skipped`() = runTest {
        val registry = FakeJobRegistry(shouldDispatchResult = false)
        val dispatcher = FakeJobDispatcher()
        val service = cloudRunService(registry, dispatcher, scope = this)

        service.launch("MS-99")
        advanceUntilIdle()

        assertTrue(dispatcher.executions.isEmpty(), "executeJob must not be called for RUNNING job")
        assertTrue(registry.inserted.isEmpty(), "No row should be inserted for RUNNING job")
    }

    // ── Dedup: COMPLETED ──────────────────────────────────────────────────────

    @Test
    fun `repeated webhook for COMPLETED job is skipped`() = runTest {
        val registry = FakeJobRegistry(shouldDispatchResult = false)
        val dispatcher = FakeJobDispatcher()
        val service = cloudRunService(registry, dispatcher, scope = this)

        service.launch("MS-99")
        advanceUntilIdle()

        assertTrue(dispatcher.executions.isEmpty(), "Completed ticket must not be re-dispatched")
    }

    // ── Dedup: FAILED ─────────────────────────────────────────────────────────

    @Test
    fun `webhook for FAILED job triggers re-dispatch`() = runTest {
        val registry = FakeJobRegistry(shouldDispatchResult = true)
        val dispatcher = FakeJobDispatcher()
        val service = cloudRunService(registry, dispatcher, scope = this)

        service.launch("MS-99")
        advanceUntilIdle()

        assertEquals(listOf("MS-99"), dispatcher.executions)
        assertEquals(listOf("MS-99"), registry.inserted)
    }

    // ── Dedup: INTERRUPTED ────────────────────────────────────────────────────

    @Test
    fun `webhook for INTERRUPTED job triggers re-dispatch`() = runTest {
        val registry = FakeJobRegistry(shouldDispatchResult = true)
        val dispatcher = FakeJobDispatcher()
        val service = cloudRunService(registry, dispatcher, scope = this)

        service.launch("MS-99")
        advanceUntilIdle()

        assertEquals(listOf("MS-99"), dispatcher.executions)
    }

    // ── INTERRUPTED + Jira status check ──────────────────────────────────────

    @Test
    fun `INTERRUPTED job with Jira In Review is marked COMPLETED and skipped`() = runTest {
        val jobId = UUID.randomUUID()
        val registry = FakeJobRegistry(shouldDispatchResult = true).apply {
            latestJob = JobRow(jobId, "MS-99", JobStatus.INTERRUPTED, null)
        }
        val dispatcher = FakeJobDispatcher()
        val service = cloudRunService(registry, dispatcher, FakeJiraApiClient(statusResponse = "In Review"), scope = this)

        service.launch("MS-99")
        advanceUntilIdle()

        assertTrue(dispatcher.executions.isEmpty(), "Must not dispatch when Jira is In Review")
        assertEquals(listOf(jobId), registry.completed)
        assertTrue(registry.inserted.isEmpty())
    }

    @Test
    fun `INTERRUPTED job with Jira Done is marked COMPLETED and skipped`() = runTest {
        val jobId = UUID.randomUUID()
        val registry = FakeJobRegistry(shouldDispatchResult = true).apply {
            latestJob = JobRow(jobId, "MS-99", JobStatus.INTERRUPTED, null)
        }
        val dispatcher = FakeJobDispatcher()
        val service = cloudRunService(registry, dispatcher, FakeJiraApiClient(statusResponse = "Done"), scope = this)

        service.launch("MS-99")
        advanceUntilIdle()

        assertTrue(dispatcher.executions.isEmpty(), "Must not dispatch when Jira is Done")
        assertEquals(listOf(jobId), registry.completed)
        assertTrue(registry.inserted.isEmpty())
    }

    @Test
    fun `INTERRUPTED job with Jira In Progress triggers re-dispatch`() = runTest {
        val jobId = UUID.randomUUID()
        val registry = FakeJobRegistry(shouldDispatchResult = true).apply {
            latestJob = JobRow(jobId, "MS-99", JobStatus.INTERRUPTED, null)
        }
        val dispatcher = FakeJobDispatcher()
        val service = cloudRunService(registry, dispatcher, FakeJiraApiClient(statusResponse = "In Progress"), scope = this)

        service.launch("MS-99")
        advanceUntilIdle()

        assertEquals(listOf("MS-99"), dispatcher.executions)
        assertTrue(registry.completed.isEmpty())
    }

    @Test
    fun `INTERRUPTED job with Jira To Do is skipped without DB change`() = runTest {
        val jobId = UUID.randomUUID()
        val registry = FakeJobRegistry(shouldDispatchResult = true).apply {
            latestJob = JobRow(jobId, "MS-99", JobStatus.INTERRUPTED, null)
        }
        val dispatcher = FakeJobDispatcher()
        val service = cloudRunService(registry, dispatcher, FakeJiraApiClient(statusResponse = "To Do"), scope = this)

        service.launch("MS-99")
        advanceUntilIdle()

        assertTrue(dispatcher.executions.isEmpty(), "Must not dispatch when Jira is To Do")
        assertTrue(registry.completed.isEmpty(), "Must not mark COMPLETED when Jira is To Do")
        assertTrue(registry.inserted.isEmpty())
    }

    // ── Dedup: no prior row ───────────────────────────────────────────────────

    @Test
    fun `webhook with no prior row dispatches fresh job`() = runTest {
        val registry = FakeJobRegistry(shouldDispatchResult = true)
        val dispatcher = FakeJobDispatcher()
        val service = cloudRunService(registry, dispatcher, scope = this)

        service.launch("MS-99")
        advanceUntilIdle()

        assertEquals(listOf("MS-99"), registry.inserted)
        assertEquals(listOf("MS-99"), dispatcher.executions)
    }

    // ── recoverInterruptedJobs: no-op when Cloud Run is disabled ──────────────

    @Test
    fun `recoverInterruptedJobs does nothing without cloudRun config`() = runTest {
        val service = AgentLaunchService(scope = this)
        service.recoverInterruptedJobs() // must not throw
    }

    // ── recoverInterruptedJobs: no running jobs ───────────────────────────────

    @Test
    fun `recoverInterruptedJobs does nothing when no running jobs exist`() = runTest {
        val registry = FakeJobRegistry()
        val dispatcher = FakeJobDispatcher()
        val service = cloudRunService(registry, dispatcher, scope = this)

        service.recoverInterruptedJobs()
        advanceUntilIdle()

        assertTrue(dispatcher.recoveries.isEmpty())
        assertTrue(registry.interrupted.isEmpty())
    }

    // ── recoverInterruptedJobs: job with executionName ────────────────────────

    @Test
    fun `recoverInterruptedJobs resumes poll for job with executionName`() = runTest {
        val executionName = "projects/my-project/locations/us-central1/operations/abc123"
        val jobId = UUID.randomUUID()
        val registry = FakeJobRegistry().apply {
            runningJobs = listOf(JobRow(jobId, "MS-99", JobStatus.RUNNING, executionName))
        }
        val dispatcher = FakeJobDispatcher(recoverResult = true)
        val service = cloudRunService(registry, dispatcher, scope = this)

        service.recoverInterruptedJobs()
        advanceUntilIdle()

        assertEquals(listOf(executionName), dispatcher.recoveries)
        assertTrue(registry.interrupted.isEmpty(), "Must not mark INTERRUPTED when execution is found")
    }

    // ── recoverInterruptedJobs: job with no executionName ─────────────────────

    @Test
    fun `recoverInterruptedJobs marks INTERRUPTED and posts comment when no executionName`() = runTest {
        val jobId = UUID.randomUUID()
        val registry = FakeJobRegistry().apply {
            runningJobs = listOf(JobRow(jobId, "MS-99", JobStatus.RUNNING, null))
        }
        val dispatcher = FakeJobDispatcher()
        val jiraClient = FakeJiraApiClient()
        val service = cloudRunService(registry, dispatcher, jiraClient, scope = this)

        service.recoverInterruptedJobs()
        advanceUntilIdle()

        assertEquals(listOf(jobId), registry.interrupted)
        assertTrue(dispatcher.recoveries.isEmpty())
        assertEquals(1, jiraClient.comments.size)
        assertEquals("MS-99", jiraClient.comments[0])
    }

    // ── recoverInterruptedJobs: execution gone on recovery ────────────────────

    @Test
    fun `recoverInterruptedJobs posts comment when execution is gone`() = runTest {
        val executionName = "projects/my-project/locations/us-central1/operations/gone"
        val jobId = UUID.randomUUID()
        val registry = FakeJobRegistry().apply {
            runningJobs = listOf(JobRow(jobId, "MS-99", JobStatus.RUNNING, executionName))
        }
        val dispatcher = FakeJobDispatcher(recoverResult = false) // execution not found
        val jiraClient = FakeJiraApiClient()
        val service = cloudRunService(registry, dispatcher, jiraClient, scope = this)

        service.recoverInterruptedJobs()
        advanceUntilIdle()

        assertEquals(listOf(executionName), dispatcher.recoveries)
        assertEquals(1, jiraClient.comments.size)
        assertEquals("MS-99", jiraClient.comments[0])
    }

    // ── PR review: Cloud Run dispatch ─────────────────────────────────────────

    @Test
    fun `launchForPrReview dispatches Cloud Run job with PR key`() = runTest {
        val registry = FakeJobRegistry(shouldDispatchResult = true)
        val dispatcher = FakeJobDispatcher()
        val service = cloudRunService(registry, dispatcher, scope = this)

        service.launchForPrReview(42)
        advanceUntilIdle()

        assertEquals(listOf("PR-42"), registry.inserted)
        assertEquals(listOf("PR-42"), dispatcher.executions)
    }

    @Test
    fun `launchForPrReview dispatches with pr-review-work jobType and PR_NUMBER identifier`() = runTest {
        val registry = FakeJobRegistry(shouldDispatchResult = true)
        val dispatcher = FakeJobDispatcher()
        val service = cloudRunService(registry, dispatcher, scope = this)

        service.launchForPrReview(42)
        advanceUntilIdle()

        assertEquals("pr-review-work", dispatcher.jobTypes.single())
        assertEquals("42", dispatcher.lastIdentifiers["PR_NUMBER"])
    }

    // ── Dispatch identifiers ──────────────────────────────────────────────────

    @Test
    fun `launchForPrReview passes pr-review-work jobType and PR_NUMBER identifier`() = runTest {
        val dispatcher = FakeJobDispatcher()
        val service = cloudRunService(FakeJobRegistry(), dispatcher, scope = this)

        service.launchForPrReview(42)
        advanceUntilIdle()

        assertEquals("pr-review-work", dispatcher.jobTypes.single())
        assertEquals("42", dispatcher.lastIdentifiers["PR_NUMBER"])
    }

    @Test
    fun `launchForConflictResolution passes conflict-resolution-work jobType and PR_NUMBER identifier`() = runTest {
        val dispatcher = FakeJobDispatcher()
        val service = cloudRunService(FakeJobRegistry(), dispatcher, scope = this)

        service.launchForConflictResolution(42)
        advanceUntilIdle()

        assertEquals("conflict-resolution-work", dispatcher.jobTypes.single())
        assertEquals("42", dispatcher.lastIdentifiers["PR_NUMBER"])
    }

    // ── Quality review dispatch ───────────────────────────────────────────────

    @Test
    fun `launchForQualityReview dispatches Cloud Run job with QUALITY key`() = runTest {
        val registry = FakeJobRegistry(shouldDispatchResult = true)
        val dispatcher = FakeJobDispatcher()
        val service = cloudRunService(registry, dispatcher, scope = this)

        service.launchForQualityReview(42, "MS-545")
        advanceUntilIdle()

        assertEquals(listOf("QUALITY-42"), registry.inserted)
        assertEquals(listOf("QUALITY-42"), dispatcher.executions)
    }

    @Test
    fun `launchForQualityReview passes pr-quality-work jobType and identifiers`() = runTest {
        val dispatcher = FakeJobDispatcher()
        val service = cloudRunService(FakeJobRegistry(), dispatcher, scope = this)

        service.launchForQualityReview(42, "MS-545")
        advanceUntilIdle()

        assertEquals("pr-quality-work", dispatcher.jobTypes.single())
        assertEquals("42", dispatcher.lastIdentifiers["PR_NUMBER"])
        // TICKET_KEY carries the synthetic dedup key so the completion event matches this row.
        assertEquals("QUALITY-42", dispatcher.lastIdentifiers["TICKET_KEY"])
        // JIRA_TICKET_KEY carries the real key so the recursion guard excludes this job.
        assertEquals("MS-545", dispatcher.lastIdentifiers["JIRA_TICKET_KEY"])
    }

    @Test
    fun `launchForQualityReview uses a distinct key from pr-review-work for the same PR`() = runTest {
        val registry = FakeJobRegistry(shouldDispatchResult = true)
        val dispatcher = FakeJobDispatcher()
        val service = cloudRunService(registry, dispatcher, scope = this)

        // Both jobs can target the same PR without one deduping the other away.
        val review = service.launchForPrReview(42)
        val quality = service.launchForQualityReview(42, "MS-545")
        advanceUntilIdle()

        assertTrue(review, "PR review must dispatch")
        assertTrue(quality, "Quality review must dispatch — distinct key, no collision")
        assertEquals(listOf("PR-42", "QUALITY-42"), dispatcher.executions)
    }

    @Test
    fun `duplicate launchForQualityReview for same PR is ignored`() = runTest {
        val registry = FakeJobRegistry(shouldDispatchResult = true)
        val gate = CompletableDeferred<Unit>()
        val dispatcher = FakeJobDispatcher(beforeExecute = { gate.await() })
        val service = cloudRunService(registry, dispatcher, scope = this)

        // First dispatch is held mid-flight (blocked in executeJob) to simulate a genuinely
        // concurrent duplicate webhook delivery, not just two sequential calls.
        val firstDeferred = async { service.launchForQualityReview(42, "MS-545") }
        runCurrent()
        val second = service.launchForQualityReview(42, "MS-545")
        gate.complete(Unit)
        val first = firstDeferred.await()
        advanceUntilIdle()

        assertTrue(first, "First launch must return true")
        assertFalse(second, "Second launch for same PR must be deduplicated")
        assertEquals(1, dispatcher.executions.size, "Only one job must be dispatched")
    }

    @Test
    fun `duplicate launchForPrReview for same PR is ignored`() = runTest {
        val registry = FakeJobRegistry(shouldDispatchResult = true)
        val gate = CompletableDeferred<Unit>()
        val dispatcher = FakeJobDispatcher(beforeExecute = { gate.await() })
        val service = cloudRunService(registry, dispatcher, scope = this)

        val firstDeferred = async { service.launchForPrReview(42) }
        runCurrent()
        val second = service.launchForPrReview(42)
        gate.complete(Unit)
        val first = firstDeferred.await()
        advanceUntilIdle()

        assertTrue(first, "First launch must return true")
        assertFalse(second, "Second launch for same PR must be deduplicated")
        assertEquals(1, dispatcher.executions.size, "Only one job must be dispatched")
    }

    // ── launchForUnblockedTicket ──────────────────────────────────────────────

    @Test
    fun `launchForUnblockedTicket dispatches with ticket-work job type`() = runTest {
        val dispatcher = FakeJobDispatcher()
        val service = cloudRunService(FakeJobRegistry(), dispatcher, scope = this)

        service.launchForUnblockedTicket("MS-521", "MS-520")
        advanceUntilIdle()

        assertEquals("ticket-work", dispatcher.jobTypes.single())
    }

    @Test
    fun `launchForUnblockedTicket passes TICKET_KEY identifier`() = runTest {
        val dispatcher = FakeJobDispatcher()
        val service = cloudRunService(FakeJobRegistry(), dispatcher, scope = this)

        service.launchForUnblockedTicket("MS-521", "MS-520")
        advanceUntilIdle()

        assertEquals("MS-521", dispatcher.lastIdentifiers["TICKET_KEY"])
        assertEquals(listOf("MS-521"), dispatcher.executions)
    }

    @Test
    fun `launchForUnblockedTicket posts Jira comment with blocker key`() = runTest {
        val dispatcher = FakeJobDispatcher()
        val jiraClient = FakeJiraApiClient()
        val service = cloudRunService(FakeJobRegistry(), dispatcher, jiraClient, scope = this)

        service.launchForUnblockedTicket("MS-521", "MS-520")
        advanceUntilIdle()

        assertEquals(1, jiraClient.comments.size, "Jira comment must be posted")
        assertEquals("MS-521", jiraClient.comments[0], "Comment must be posted on the unblocked ticket")
    }

    @Test
    fun `launchForUnblockedTicket is deduplicated by activeKeys for re-entrant Jira webhook`() = runTest {
        val registry = FakeJobRegistry(shouldDispatchResult = true)
        val gate = CompletableDeferred<Unit>()
        val dispatcher = FakeJobDispatcher(beforeExecute = { gate.await() })
        val service = cloudRunService(registry, dispatcher, scope = this)

        // Simulate: GitHub webhook calls launchForUnblockedTicket and activeKeys is set synchronously,
        // then held mid-flight (blocked in executeJob) while a re-entrant Jira webhook — fired by the
        // bot-initiated In Progress transition — calls launch for the same key concurrently.
        // The activeKeys gate must reject the re-entrant call while the first is still in flight.
        val firstDeferred = async { service.launchForUnblockedTicket("MS-521", "MS-520") }
        runCurrent()
        val reentrant = service.launch("MS-521")
        gate.complete(Unit)
        val first = firstDeferred.await()
        advanceUntilIdle()

        assertTrue(first, "Initial dispatch must succeed")
        assertFalse(reentrant, "Re-entrant dispatch must be rejected by activeKeys gate")
        assertEquals(1, dispatcher.executions.size, "Only one Cloud Run job must be dispatched")
    }

    @Test
    fun `launchForUnblockedTicket transitions ticket to In Progress`() = runTest {
        val dispatcher = FakeJobDispatcher()
        val jiraClient = FakeJiraApiClient()
        val service = cloudRunService(FakeJobRegistry(), dispatcher, jiraClient, scope = this)

        service.launchForUnblockedTicket("MS-521", "MS-520")
        advanceUntilIdle()

        assertEquals(listOf("MS-521"), jiraClient.transitions, "In Progress transition must be applied")
    }

    @Test
    fun `launch without blockerKey does not transition to In Progress`() = runTest {
        val dispatcher = FakeJobDispatcher()
        val jiraClient = FakeJiraApiClient()
        val service = cloudRunService(FakeJobRegistry(), dispatcher, jiraClient, scope = this)

        service.launch("MS-521")
        advanceUntilIdle()

        assertTrue(jiraClient.transitions.isEmpty(), "Direct launch must not trigger In Progress transition")
    }

    @Test
    fun `launchForUnblockedTicket with shouldDispatch false is a no-op`() = runTest {
        val registry = FakeJobRegistry(shouldDispatchResult = false)
        val dispatcher = FakeJobDispatcher()
        val jiraClient = FakeJiraApiClient()
        val service = cloudRunService(registry, dispatcher, jiraClient, scope = this)

        service.launchForUnblockedTicket("MS-521", "MS-520")
        advanceUntilIdle()

        assertTrue(dispatcher.executions.isEmpty(), "Must not dispatch when shouldDispatch returns false")
        assertTrue(jiraClient.comments.isEmpty(), "Must not post comment when dedup rejects dispatch")
    }
}
