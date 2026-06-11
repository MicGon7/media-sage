package com.mediasage.pipeline.pipeline

import com.mediasage.pipeline.core.JobStatus
import com.mediasage.pipeline.support.FullPipelineScenarioBase
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import java.util.UUID

/**
 * Full pipeline scenario: failure recovery after orchestrator restart.
 *
 * Simulates an orchestrator restart with a RUNNING job that has a non-existent execution.
 * Calls [AgentLaunchService.recoverInterruptedJobs], which makes a real Cloud Run API call
 * to check the execution status. The execution returns 404 (fake name), triggering the
 * INTERRUPTED path and marking the job accordingly in Supabase.
 *
 * Required env vars: SUPABASE_DB_URL, GCP_PROJECT_ID, GOOGLE_CREDENTIALS_BASE64
 *
 * ⚠️ This test makes a real authenticated call to the Cloud Run Jobs API.
 *    It does NOT dispatch a worker — it only calls the recovery/status-check path.
 */
@Tag("e2e")
class FailureRecoveryE2eTest : FullPipelineScenarioBase() {

    override fun scenarioName() = "Failure Recovery"

    @Test
    fun `recoverInterruptedJobs marks orphaned RUNNING job as INTERRUPTED`() = runBlocking {
        val testKey = "MS-E2E-RECOVER-${UUID.randomUUID().toString().take(8).uppercase()}"
        // Use a well-formed but non-existent execution name so Cloud Run API returns 404
        val fakeExecution = "projects/${config.gcpProjectId}/locations/${config.gcpRegion}" +
            "/jobs/${config.gcpJobName}/executions/fake-e2e-recover-abc123"

        println("\n Starting failure recovery scenario")
        println(" Ticket: $testKey | Fake execution: $fakeExecution\n")

        // Setup: insert a RUNNING job as if the orchestrator had started it before crashing
        val jobId = jobRegistry.insert(testKey, "e2e recovery test prompt")
        jobRegistry.markRunning(jobId, fakeExecution)
        report.checkpoint("RUNNING job inserted in Supabase", true)

        // Act: simulate orchestrator restart — recover should detect the execution is gone
        service.recoverInterruptedJobs()

        // Allow the recovery coroutine to complete
        delay(3_000)

        val job = jobRegistry.findLatestJob(testKey)
        report.checkpoint(
            "Job marked INTERRUPTED (execution was gone)",
            job?.status == JobStatus.INTERRUPTED
        )

        report.print()
        report.assertAllPassed()
    }
}
