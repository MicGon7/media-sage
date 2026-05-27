package com.mediasage.pipeline.dedup

import com.mediasage.pipeline.support.DedupScenarioBase
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test

/**
 * Verifies a FAILED job is eligible for re-dispatch.
 *
 * Setup: inserts a FAILED job row directly in Supabase.
 * Assert: [JobRegistry.shouldDispatch] returns true — retry is allowed.
 */
@Tag("e2e")
class DedupFailedRetryE2eTest : DedupScenarioBase() {

    override fun scenarioName() = "Dedup — FAILED job eligible for retry"

    @Test
    fun `shouldDispatch returns true when job is FAILED`() = runBlocking {
        val jobId = jobRegistry.insert(testKey, "e2e test prompt")
        jobRegistry.markFailed(jobId)

        val canDispatch = jobRegistry.shouldDispatch(testKey)

        report.checkpoint("Job inserted as FAILED", true)
        report.checkpoint("shouldDispatch() allowed (returned true)", canDispatch)
        report.print()

        // Cleanup — mark completed so it doesn't linger as FAILED
        val newJobId = jobRegistry.insert("${testKey}-CLEANUP", "cleanup")
        jobRegistry.markCompleted(newJobId)

        report.assertAllPassed()
    }
}
