package com.mediasage.pipeline.dedup

import com.mediasage.pipeline.support.DedupScenarioBase
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test

/**
 * Verifies the dedup gate blocks a second dispatch when a job is already RUNNING.
 *
 * Setup: inserts a RUNNING job row directly in Supabase.
 * Assert: [JobRegistry.shouldDispatch] returns false — Cloud Run is never called.
 */
@Tag("e2e")
class DedupRunningE2eTest : DedupScenarioBase() {

    override fun scenarioName() = "Dedup — RUNNING blocks second dispatch"

    @Test
    fun `shouldDispatch returns false when job is RUNNING`() = runBlocking {
        val jobId = jobRegistry.insert(testKey, "e2e test prompt")
        jobRegistry.markRunning(jobId, "projects/fake/locations/fake/jobs/fake/executions/fake-running")

        val canDispatch = jobRegistry.shouldDispatch(testKey)

        report.checkpoint("Job inserted as RUNNING", true)
        report.checkpoint("shouldDispatch() blocked (returned false)", !canDispatch)
        report.print()

        // Cleanup
        jobRegistry.markCompleted(jobId)

        report.assertAllPassed()
    }
}
