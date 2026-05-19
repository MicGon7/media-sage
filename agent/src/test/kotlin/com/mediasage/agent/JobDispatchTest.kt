package com.mediasage.agent

import com.mediasage.agent.db.JobRegistry
import com.mediasage.agent.db.JobRow
import com.mediasage.agent.db.JobStatus
import com.mediasage.agent.service.AgentLaunchService
import com.mediasage.agent.service.CloudRunDispatch
import com.mediasage.agent.service.JobDispatcher
import com.mediasage.agent.service.JiraCommentPoster
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
        val inserted = mutableListOf<String>()          // ticket keys inserted
        val interrupted = mutableListOf<UUID>()
        val failed = mutableListOf<UUID>()
        var runningJobs: List<JobRow> = emptyList()

        override suspend fun shouldDispatch(ticketKey: String) = shouldDispatchResult
        override suspend fun insert(ticketKey: String, prompt: String): UUID {
            inserted.add(ticketKey)
            return UUID.randomUUID()
        }
        override suspend fun markRunning(jobId: UUID, executionName: String) = Unit
        override suspend fun markCompleted(jobId: UUID) = Unit
        override suspend fun markFailed(jobId: UUID) { failed.add(jobId) }
        override suspend fun markInterrupted(jobId: UUID) { interrupted.add(jobId) }
        override suspend fun findRunningJobs() = runningJobs
    }

    private class FakeJobDispatcher(private val recoverResult: Boolean = true) : JobDispatcher {
        val executions = mutableListOf<String>()         // ticket keys passed to executeJob
        val recoveries = mutableListOf<String>()         // executionNames passed to recoverJob

        override suspend fun executeJob(jobId: UUID, ticketKey: String, prompt: String): Boolean {
            executions.add(ticketKey)
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
        scope: TestScope,
    ) = AgentLaunchService(
        repoPath = "/repo",
        scope = scope,
        cloudRun = CloudRunDispatch(dispatcher, registry),
        jiraCommentPoster = poster
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
        val service = AgentLaunchService(repoPath = "/repo", scope = this)
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
}
