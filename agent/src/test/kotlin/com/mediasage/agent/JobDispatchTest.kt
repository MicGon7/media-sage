package com.mediasage.agent

import com.mediasage.agent.db.JobRegistry
import com.mediasage.agent.db.JobRow
import com.mediasage.agent.db.JobStatus
import com.mediasage.agent.db.WorkerMetrics
import com.mediasage.agent.service.AgentLaunchService
import com.mediasage.agent.service.CloudRunDispatch
import com.mediasage.agent.service.JobDispatcher
import com.mediasage.agent.service.JiraCommentPoster
import com.mediasage.agent.service.JiraTicketStatusChecker
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
        override suspend fun insert(ticketKey: String, prompt: String): UUID {
            inserted.add(ticketKey)
            return UUID.randomUUID()
        }
        override suspend fun markRunning(jobId: UUID, executionName: String) = Unit
        override suspend fun markCompleted(jobId: UUID, metrics: WorkerMetrics?) { completed.add(jobId) }
        override suspend fun markFailed(jobId: UUID) { failed.add(jobId) }
        override suspend fun markInterrupted(jobId: UUID) { interrupted.add(jobId) }
        override suspend fun findRunningJobs() = runningJobs
        override suspend fun findRunningByTicketKey(ticketKey: String): JobRow? =
            runningJobs.firstOrNull { it.ticketKey == ticketKey }
    }

    private class FakeJiraStatusChecker(private val status: String?) : JiraTicketStatusChecker {
        override suspend fun getTicketStatus(ticketKey: String) = status
    }

    private class FakeJobDispatcher(private val recoverResult: Boolean = true) : JobDispatcher {
        val executions = mutableListOf<String>()         // ticket keys passed to executeJob
        val prompts = mutableListOf<String>()            // prompts passed to executeJob
        val recoveries = mutableListOf<String>()         // executionNames passed to recoverJob

        override suspend fun executeJob(jobId: UUID, ticketKey: String, prompt: String, jiraTicketKey: String?): Boolean {
            executions.add(ticketKey)
            prompts.add(prompt)
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
        jiraStatusChecker = jiraStatusChecker
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

        service.launchForPrReview("MS-42", 42, "feature/MS-42-some-feature", "Please fix this.", "jane-reviewer")
        advanceUntilIdle()

        assertEquals(listOf("PR-42"), registry.inserted)
        assertEquals(listOf("PR-42"), dispatcher.executions)
    }

    @Test
    fun `launchForPrReview prompt includes reviewer login for re-request`() = runTest {
        val registry = FakeJobRegistry(shouldDispatchResult = true)
        val dispatcher = FakeJobDispatcher()
        val service = cloudRunService(registry, dispatcher, scope = this)

        service.launchForPrReview("MS-42", 42, "feature/MS-42-some-feature", "Please fix this.", "jane-reviewer")
        advanceUntilIdle()

        assertTrue(dispatcher.prompts.single().contains("jane-reviewer"),
            "Prompt must include reviewer login for gh pr review-request")
    }

    @Test
    fun `launchForCommentReview dispatches Cloud Run job with PR key`() = runTest {
        val registry = FakeJobRegistry(shouldDispatchResult = true)
        val dispatcher = FakeJobDispatcher()
        val service = cloudRunService(registry, dispatcher, scope = this)

        service.launchForCommentReview("MS-42", 42, "feature/MS-42-some-feature", "What does this do?")
        advanceUntilIdle()

        assertEquals(listOf("PR-42"), registry.inserted)
        assertEquals(listOf("PR-42"), dispatcher.executions)
    }

    // ── Relevant files warning ────────────────────────────────────────────────

    @Test
    fun `launch dispatches when ticket content is missing Relevant files section`() = runTest {
        val registry = FakeJobRegistry(shouldDispatchResult = true)
        val dispatcher = FakeJobDispatcher()
        val service = cloudRunService(registry, dispatcher, scope = this)

        // Dispatch must not be blocked — warning is advisory only
        service.launch("MS-99", ticketContent = "## Description\nDo the thing.\n\n## Acceptance criteria\n- [ ] Done")
        advanceUntilIdle()

        assertEquals(listOf("MS-99"), registry.inserted, "Dispatch must proceed despite missing Relevant files")
        assertEquals(listOf("MS-99"), dispatcher.executions)
    }

    @Test
    fun `launch dispatches normally when ticket content contains Relevant files section`() = runTest {
        val registry = FakeJobRegistry(shouldDispatchResult = true)
        val dispatcher = FakeJobDispatcher()
        val service = cloudRunService(registry, dispatcher, scope = this)

        service.launch("MS-99", ticketContent = "## Relevant files\n* foo.kt — entry point\n")
        advanceUntilIdle()

        assertEquals(listOf("MS-99"), registry.inserted)
        assertEquals(listOf("MS-99"), dispatcher.executions)
    }

    @Test
    fun `launch dispatches normally when ticket content is null`() = runTest {
        val registry = FakeJobRegistry(shouldDispatchResult = true)
        val dispatcher = FakeJobDispatcher()
        val service = cloudRunService(registry, dispatcher, scope = this)

        service.launch("MS-99", ticketContent = null)
        advanceUntilIdle()

        assertEquals(listOf("MS-99"), registry.inserted)
        assertEquals(listOf("MS-99"), dispatcher.executions)
    }

    @Test
    fun `duplicate launchForPrReview for same PR is ignored`() = runTest {
        val registry = FakeJobRegistry(shouldDispatchResult = true)
        val dispatcher = FakeJobDispatcher()
        val service = cloudRunService(registry, dispatcher, scope = this)

        val first = service.launchForPrReview("MS-42", 42, "feature/MS-42", "Fix this.", "jane")
        val second = service.launchForPrReview("MS-42", 42, "feature/MS-42", "Fix this too.", "jane")
        advanceUntilIdle()

        assertTrue(first, "First launch must return true")
        assertFalse(second, "Second launch for same PR must be deduplicated")
        assertEquals(1, dispatcher.executions.size, "Only one job must be dispatched")
    }
}
