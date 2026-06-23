package com.mediasage.orchestrator

import com.mediasage.pipeline.core.JobRegistry
import com.mediasage.pipeline.core.JobRow
import com.mediasage.pipeline.core.JobStatus
import com.mediasage.pipeline.core.WorkerMetrics
import com.mediasage.orchestrator.service.AgentLaunchService
import com.mediasage.orchestrator.service.CloudRunDispatch
import com.mediasage.orchestrator.service.JobDispatcher
import com.mediasage.orchestrator.service.JiraCommentPoster
import com.mediasage.orchestrator.service.JiraTicketStatusChecker
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
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
 * [JiraCommentPoster] replace all I/O. Tests use [runTest] + [advanceUntilIdle]
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
        override suspend fun markCompleted(jobId: UUID, metrics: WorkerMetrics?, envStartupMs: Long?) { completed.add(jobId) }
        override suspend fun markFailed(jobId: UUID, failedGate: String?, modelVersion: String?) { failed.add(jobId) }
        override suspend fun markInterrupted(jobId: UUID) { interrupted.add(jobId) }
        override suspend fun findRunningJobs() = runningJobs
        override suspend fun findRunningByTicketKey(ticketKey: String): JobRow? =
            runningJobs.firstOrNull { it.ticketKey == ticketKey }
    }

    private class FakeJiraStatusChecker(private val status: String?) : JiraTicketStatusChecker {
        override suspend fun getTicketStatus(ticketKey: String) = status
    }

    private class FakeJobDispatcher(private val recoverResult: Boolean = true) : JobDispatcher {
        val executions = mutableListOf<String>()
        val jobTypes = mutableListOf<String>()
        var lastIdentifiers: Map<String, String> = emptyMap()
        val recoveries = mutableListOf<String>()

        override suspend fun executeJob(
            jobId: UUID, ticketKey: String, jobType: String, identifiers: Map<String, String>, jobNameOverride: String?
        ): Boolean {
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

    private class FakeJiraCommentPoster : JiraCommentPoster {
        val comments = mutableListOf<Pair<String, String>>() // ticketKey to body

        override suspend fun addComment(ticketKey: String, body: String) {
            comments.add(ticketKey to body)
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun cloudRunService(
        registry: FakeJobRegistry,
        dispatcher: FakeJobDispatcher,
        poster: FakeJiraCommentPoster = FakeJiraCommentPoster(),
        jiraStatusChecker: JiraTicketStatusChecker? = null,
        scope: TestScope,
    ) = AgentLaunchService(
        scope = scope,
        cloudRun = CloudRunDispatch(dispatcher, registry),
        jiraCommentPoster = poster,
        jiraStatusChecker = jiraStatusChecker,
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
        val service = cloudRunService(registry, dispatcher, jiraStatusChecker = FakeJiraStatusChecker("In Review"), scope = this)

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
        val service = cloudRunService(registry, dispatcher, jiraStatusChecker = FakeJiraStatusChecker("Done"), scope = this)

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
        val service = cloudRunService(registry, dispatcher, jiraStatusChecker = FakeJiraStatusChecker("In Progress"), scope = this)

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
        val service = cloudRunService(registry, dispatcher, jiraStatusChecker = FakeJiraStatusChecker("To Do"), scope = this)

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
        val poster = FakeJiraCommentPoster()
        val service = cloudRunService(registry, dispatcher, poster, scope = this)

        service.recoverInterruptedJobs()
        advanceUntilIdle()

        assertEquals(listOf(jobId), registry.interrupted)
        assertTrue(dispatcher.recoveries.isEmpty())
        assertEquals(1, poster.comments.size)
        assertEquals("MS-99", poster.comments[0].first)
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
        val poster = FakeJiraCommentPoster()
        val service = cloudRunService(registry, dispatcher, poster, scope = this)

        service.recoverInterruptedJobs()
        advanceUntilIdle()

        assertEquals(listOf(executionName), dispatcher.recoveries)
        assertEquals(1, poster.comments.size)
        assertEquals("MS-99", poster.comments[0].first)
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

    // ── Judge: dispatch after ticket-work completion ──────────────────────────

    @Test
    fun `launchForJudge dispatches Cloud Run job with JUDGE key`() = runTest {
        val registry = FakeJobRegistry(shouldDispatchResult = true)
        val dispatcher = FakeJobDispatcher()
        val service = cloudRunService(registry, dispatcher, scope = this)

        service.launchForJudge("MS-42")
        advanceUntilIdle()

        assertEquals(listOf("JUDGE-MS-42"), registry.inserted)
        assertEquals(listOf("JUDGE-MS-42"), dispatcher.executions)
    }

    @Test
    fun `launchForJudge passes judge-work jobType and TICKET_KEY identifier`() = runTest {
        val dispatcher = FakeJobDispatcher()
        val service = cloudRunService(FakeJobRegistry(), dispatcher, scope = this)

        service.launchForJudge("MS-42")
        advanceUntilIdle()

        assertEquals("judge-work", dispatcher.jobTypes.single())
        assertEquals("MS-42", dispatcher.lastIdentifiers["TICKET_KEY"])
    }

    @Test
    fun `duplicate launchForPrReview for same PR is ignored`() = runTest {
        val registry = FakeJobRegistry(shouldDispatchResult = true)
        val dispatcher = FakeJobDispatcher()
        val service = cloudRunService(registry, dispatcher, scope = this)

        val first = service.launchForPrReview(42)
        val second = service.launchForPrReview(42)
        advanceUntilIdle()

        assertTrue(first, "First launch must return true")
        assertFalse(second, "Second launch for same PR must be deduplicated")
        assertEquals(1, dispatcher.executions.size, "Only one job must be dispatched")
    }
}
