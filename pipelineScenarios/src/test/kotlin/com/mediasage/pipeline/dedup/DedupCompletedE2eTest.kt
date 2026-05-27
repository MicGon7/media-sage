package com.mediasage.pipeline.dedup

import com.mediasage.pipeline.support.DedupScenarioBase
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test

/**
 * Verifies the dedup gate permanently blocks dispatch for a COMPLETED job.
 *
 * This is the post-deploy canary scenario — lightweight, no Cloud Run, proves
 * Supabase is reachable and the orchestrator dedup logic is intact.
 *
 * Setup: inserts a COMPLETED job row directly in Supabase.
 * Assert: [JobRegistry.shouldDispatch] returns false.
 */
@Tag("e2e")
class DedupCompletedE2eTest : DedupScenarioBase() {

    override fun scenarioName() = "Dedup — COMPLETED permanently blocks dispatch"

    @Test
    fun `shouldDispatch returns false when job is COMPLETED`() = runBlocking {
        val jobId = jobRegistry.insert(testKey, "e2e test prompt")
        jobRegistry.markRunning(jobId, "projects/fake/locations/fake/jobs/fake/executions/fake-completed")
        jobRegistry.markCompleted(jobId)

        val canDispatch = jobRegistry.shouldDispatch(testKey)

        report.checkpoint("Job inserted as COMPLETED", true)
        report.checkpoint("shouldDispatch() blocked (returned false)", !canDispatch)
        report.print()

        report.assertAllPassed()
    }
}
