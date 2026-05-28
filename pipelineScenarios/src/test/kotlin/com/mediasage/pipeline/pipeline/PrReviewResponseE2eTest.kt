package com.mediasage.pipeline.pipeline

import com.mediasage.agent.db.JobStatus
import com.mediasage.pipeline.support.FullPipelineScenarioBase
import com.mediasage.pipeline.support.GitHubFixtureClient
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import java.util.UUID
import kotlin.time.Duration.Companion.minutes

private val TIMEOUT = 15.minutes.inWholeMilliseconds

/**
 * Full pipeline scenario: PR review response.
 *
 * Creates a real PR on GitHub via [GitHubFixtureClient], then simulates a reviewer
 * submitting a changes_requested review. Dispatches a real Cloud Run Job, polls until
 * completion, and validates each checkpoint.
 *
 * Setup:
 * 1. Sync [E2E_BASE_BRANCH] to main (force-reset discards prior test commits)
 * 2. Create a short-lived feature branch off [E2E_BASE_BRANCH]
 * 3. Push a trivial scratch commit to give the branch a unique diff
 * 4. Open a PR from the feature branch targeting [E2E_BASE_BRANCH]
 *
 * Required env vars: SUPABASE_DB_URL, GCP_PROJECT_ID, GOOGLE_CREDENTIALS_BASE64, GITHUB_TOKEN
 *
 * ⚠️ Dispatches a real Cloud Run Job. Only ever writes to e2e-scratch/ — real code is never affected.
 */
@Tag("e2e")
class PrReviewResponseE2eTest : FullPipelineScenarioBase() {

    override fun scenarioName() = "PR Review Response"

    private val shortId = UUID.randomUUID().toString().take(8)
    private val ticketKey = "MS-E2E-${shortId.uppercase()}"
    private val branchName = "e2e/review-$shortId"
    private val scratchPath = "e2e-scratch/review-$shortId.txt"
    private var prNumber: Int = -1

    @BeforeEach
    fun setUpFixture() = runBlocking {
        fixture.syncBranchWithMain(GitHubFixtureClient.E2E_BASE_BRANCH)
        fixture.createBranch(branchName)
        fixture.pushCommit(
            branch = branchName,
            path = scratchPath,
            content = "initial content",
            message = "e2e: add scratch file"
        )
        prNumber = fixture.openPullRequest(
            branch = branchName,
            title = "[$ticketKey] E2E PR review response fixture",
            body = "Automated E2E fixture PR — safe to close"
        )
    }

    @AfterEach
    fun tearDownFixture() = runBlocking {
        fixture.closePullRequest(prNumber)
        fixture.deleteBranch(branchName)
    }

    @Test
    fun `pr review response scenario`() = runBlocking {
        val reviewComment = "Please extract this logic into a helper function."
        val reviewerLogin = "michael-gonzalez-dev"

        println("\n Starting PR review response scenario")
        println(" Ticket: $ticketKey | PR: #$prNumber | Branch: $branchName\n")

        val dispatched = service.launchForPrReview(ticketKey, prNumber, branchName, reviewComment, reviewerLogin)
        report.checkpoint("Cloud Run Job dispatched", dispatched)

        if (!dispatched) {
            report.print()
            report.assertAllPassed()
            return@runBlocking
        }

        val dedupKey = "PR-$prNumber"
        val finalStatus = waitForCompletion(dedupKey, TIMEOUT)
        report.checkpoint("Job reached terminal state (${finalStatus ?: "TIMEOUT"})", finalStatus == JobStatus.COMPLETED)

        val job = jobRegistry.findLatestJob(dedupKey)
        report.checkpoint("Job COMPLETED in Supabase", job?.status == JobStatus.COMPLETED)

        report.print()
        report.assertAllPassed()
    }
}
