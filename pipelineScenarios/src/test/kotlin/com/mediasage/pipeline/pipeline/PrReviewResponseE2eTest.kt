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

private val TIMEOUT = 20.minutes.inWholeMilliseconds

/**
 * Full pipeline scenario: PR review response via webhook.
 *
 * Creates a real PR on GitHub, then simulates a reviewer submitting a `changes_requested` review
 * by POSTing directly to the live orchestrator with a valid HMAC signature. The orchestrator
 * dispatches a real Cloud Run Job, the worker pushes a fix commit and re-requests review, and
 * the test polls Supabase until COMPLETED.
 *
 * This tests the full orchestrator code path:
 * - HMAC signature verification
 * - Payload parsing and bot identity check (pull_request.user.login == botLogin)
 * - Ticket key extraction from branch ref ([A-Z]+-\d+ regex)
 * - Supabase dedup gate
 * - Cloud Run Job dispatch
 *
 * Branch naming uses `feature/MS-257-e2e-review-{id}` so the ticket key regex extracts
 * `MS-257` — no Jira lookup needed since the orchestrator only checks PR author after MS-258.
 *
 * Setup:
 * 1. Sync [E2E_BASE_BRANCH] to main
 * 2. Create feature branch off [E2E_BASE_BRANCH]
 * 3. Push a trivial scratch commit so the branch has a unique diff
 * 4. Open a PR from the feature branch targeting [E2E_BASE_BRANCH]
 *
 * Required env vars: SUPABASE_DB_URL, GCP_PROJECT_ID, GOOGLE_CREDENTIALS_BASE64,
 * ORCHESTRATOR_URL, GITHUB_WEBHOOK_SECRET, GH_TOKEN
 *
 * ⚠️ Dispatches a real Cloud Run Job. Only ever writes to e2e-scratch/ — real code is never affected.
 */
@Tag("e2e")
class PrReviewResponseE2eTest : FullPipelineScenarioBase() {

    override fun scenarioName() = "PR Review Response"

    private val shortId = UUID.randomUUID().toString().take(8)
    private lateinit var branchName: String
    private val scratchPath = "e2e-scratch/review-$shortId.txt"
    private var prNumber: Int = -1

    @BeforeEach
    fun setUpFixture() = runBlocking {
        branchName = "feature/${config.target.fixtureTicketKey}-e2e-review-$shortId"
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
            title = "[$branchName] E2E PR review response fixture",
            body = "Automated E2E fixture PR — safe to close"
        )
    }

    @AfterEach
    fun tearDownFixture() = runBlocking {
        fixture.closePullRequest(prNumber)
    }

    @Test
    fun `pr review response scenario`() = runBlocking {
        println("\n Starting PR review response scenario")
        println(" Branch: $branchName | PR: #$prNumber\n")

        postWebhook("pull_request_review", reviewWebhookPayload(prNumber, branchName))
        report.checkpoint("Review webhook accepted by orchestrator", true)

        val dedupKey = "PR-$prNumber"
        val finalStatus = waitForCompletion(dedupKey, TIMEOUT)
        report.checkpoint(
            "Job reached terminal state (${finalStatus ?: "TIMEOUT"})",
            finalStatus == JobStatus.COMPLETED
        )

        val job = jobRegistry.findLatestJob(dedupKey)
        report.checkpoint("Job COMPLETED in Supabase", job?.status == JobStatus.COMPLETED)

        // Delete branch after the job reaches terminal state so the worker doesn't
        // hit a missing upstream error mid-run.
        fixture.deleteBranch(branchName)

        report.print()
        report.assertAllPassed()
    }

    private fun reviewWebhookPayload(prNumber: Int, branchName: String) = """
        {
          "action": "submitted",
          "sender": {"login": "michael-gonzalez-dev"},
          "pull_request": {
            "number": $prNumber,
            "head": {"ref": "$branchName"},
            "user": {"login": "media-sage-worker[bot]"}
          },
          "review": {
            "state": "changes_requested",
            "body": "Please add a comment explaining what this file is for."
          }
        }
    """.trimIndent()
}
