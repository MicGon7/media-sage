package com.mediasage.pipeline.pipeline

import com.mediasage.agent.db.JobStatus
import com.mediasage.pipeline.support.FullPipelineScenarioBase
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.minutes

private val TIMEOUT = 15.minutes.inWholeMilliseconds

/**
 * Full pipeline scenario: conflict resolution.
 *
 * Simulates a PR being ejected from the merge queue due to a merge conflict.
 * Dispatches a real Cloud Run Job, polls until completion, and validates each checkpoint.
 *
 * Required env vars: SUPABASE_DB_URL, GCP_PROJECT_ID, GOOGLE_CREDENTIALS_BASE64
 * Optional env vars: E2E_TICKET_KEY, E2E_PR_NUMBER, E2E_BRANCH_REF
 *
 * ⚠️ This test dispatches a real Cloud Run Job and will push a real commit to the branch.
 */
@Tag("e2e")
class ConflictResolutionE2eTest : FullPipelineScenarioBase() {

    override fun scenarioName() = "Conflict Resolution"

    @Test
    fun `conflict resolution scenario`() = runBlocking {
        val ticketKey = config.e2eTicketKey
        val prNumber = config.e2ePrNumber
        val branchRef = config.e2eBranchRef

        println("\n Starting conflict resolution scenario")
        println(" Ticket: $ticketKey | PR: #$prNumber | Branch: $branchRef\n")

        val dispatched = service.launchForConflictResolution(ticketKey, prNumber, branchRef)
        report.checkpoint("Cloud Run Job dispatched", dispatched)

        if (!dispatched) {
            report.print()
            report.assertAllPassed()
            return@runBlocking
        }

        val dedupKey = "CONFLICT-$prNumber"
        val finalStatus = waitForCompletion(dedupKey, TIMEOUT)
        report.checkpoint("Job reached terminal state (${finalStatus ?: "TIMEOUT"})", finalStatus == JobStatus.COMPLETED)

        val job = jobRegistry.findLatestJob(dedupKey)
        report.checkpoint("Job COMPLETED in Supabase", job?.status == JobStatus.COMPLETED)

        report.print()
        report.assertAllPassed()
    }
}
