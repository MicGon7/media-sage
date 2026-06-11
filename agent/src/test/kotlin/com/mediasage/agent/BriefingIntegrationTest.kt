package com.mediasage.agent

import com.mediasage.pipeline.core.JobRegistry
import com.mediasage.pipeline.core.JobRow
import com.mediasage.pipeline.core.JobStatus
import com.mediasage.pipeline.core.WorkerMetrics
import com.mediasage.agent.service.AgentLaunchService
import com.mediasage.agent.service.BriefingContext
import com.mediasage.agent.service.BriefingService
import com.mediasage.agent.service.CloudRunDispatch
import com.mediasage.agent.service.JobDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Verifies that [AgentLaunchService] correctly appends the briefing to the worker prompt
 * when [BriefingService] returns a non-null result, and leaves the prompt unchanged when
 * it returns null (failure fallback).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class BriefingIntegrationTest {

    private val briefingText = "Fix line 42 in AgentLauncher.kt."

    // Synchronous fake — avoids the MockEngine dispatcher mismatch in TestCoroutineScheduler.
    // AgentLaunchService depends on the BriefingService interface, so we inject a fake here
    // rather than a HaikuBriefingService backed by MockEngine. HaikuBriefingService is tested
    // separately in BriefingServiceTest.
    private class FakeBriefingService(private val result: String?) : BriefingService {
        override suspend fun brief(context: BriefingContext): String? = result
    }

    private class CapturingDispatcher : JobDispatcher {
        val prompts = mutableListOf<String>()
        override suspend fun executeJob(
            jobId: UUID, ticketKey: String, prompt: String, jiraTicketKey: String?, jobNameOverride: String?
        ): Boolean {
            prompts.add(prompt)
            return true
        }
    }

    private class AlwaysDispatchRegistry : JobRegistry {
        override suspend fun shouldDispatch(ticketKey: String) = true
        override suspend fun insert(ticketKey: String, prompt: String): UUID = UUID.randomUUID()
        override suspend fun markRunning(jobId: UUID, executionName: String) = Unit
        override suspend fun markCompleted(jobId: UUID, metrics: WorkerMetrics?) = Unit
        override suspend fun markFailed(jobId: UUID) = Unit
        override suspend fun markInterrupted(jobId: UUID) = Unit
        override suspend fun findRunningJobs(): List<JobRow> = emptyList()
        override suspend fun findLatestJob(ticketKey: String): JobRow? = null
        override suspend fun findRunningByTicketKey(ticketKey: String): JobRow? = null
    }

    private fun makeService(briefingService: BriefingService?, scope: TestScope): Pair<AgentLaunchService, CapturingDispatcher> {
        val dispatcher = CapturingDispatcher()
        val service = AgentLaunchService(
            scope = scope,
            cloudRun = CloudRunDispatch(dispatcher, AlwaysDispatchRegistry()),
            briefingService = briefingService,
        )
        return service to dispatcher
    }

    @Test
    fun `briefing appended to prompt when service returns non-null`() = runTest {
        val (service, dispatcher) = makeService(FakeBriefingService(briefingText), this)
        service.launch("MS-1", "Add health endpoint")
        advanceUntilIdle()
        assertTrue(dispatcher.prompts.single().contains("## Agent Briefing"))
        assertTrue(dispatcher.prompts.single().contains(briefingText))
    }

    @Test
    fun `base prompt unchanged when briefing service returns null`() = runTest {
        val (service, dispatcher) = makeService(FakeBriefingService(null), this)
        service.launch("MS-1", "Add health endpoint")
        advanceUntilIdle()
        assertFalse(dispatcher.prompts.single().contains("## Agent Briefing"))
    }

    @Test
    fun `base prompt unchanged when no briefing service configured`() = runTest {
        val (service, dispatcher) = makeService(null, this)
        service.launch("MS-1", "Add health endpoint")
        advanceUntilIdle()
        assertFalse(dispatcher.prompts.single().contains("## Agent Briefing"))
    }

    @Test
    fun `briefing not appended for PR review dispatch`() = runTest {
        val (service, dispatcher) = makeService(FakeBriefingService(briefingText), this)
        service.launchForPrReview("MS-1", 42, "feature/MS-1-fix", "Add periods", "reviewer")
        advanceUntilIdle()
        assertFalse(dispatcher.prompts.single().contains("## Agent Briefing"))
    }

    @Test
    fun `briefing appended for conflict resolution dispatch`() = runTest {
        val (service, dispatcher) = makeService(FakeBriefingService(briefingText), this)
        service.launchForConflictResolution("MS-1", 42, "feature/MS-1-fix", "main")
        advanceUntilIdle()
        assertTrue(dispatcher.prompts.single().contains("## Agent Briefing"))
    }

    @Test
    fun `briefing appended for comment review dispatch`() = runTest {
        val (service, dispatcher) = makeService(FakeBriefingService(briefingText), this)
        service.launchForCommentReview("MS-1", 42, "feature/MS-1-fix", "Why suspend?")
        advanceUntilIdle()
        assertTrue(dispatcher.prompts.single().contains("## Agent Briefing"))
    }
}
