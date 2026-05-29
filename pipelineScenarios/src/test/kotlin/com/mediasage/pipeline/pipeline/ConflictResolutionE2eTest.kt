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

private val TIMEOUT = 40.minutes.inWholeMilliseconds

/**
 * Full pipeline scenario: conflict resolution via webhook.
 *
 * Creates a real merge conflict on GitHub, then simulates the GitHub merge queue firing a
 * `pull_request dequeued merge_conflict` event by POSTing directly to the live orchestrator
 * with a valid HMAC signature. The orchestrator dispatches a real Cloud Run Job, the worker
 * rebases the branch and resolves the conflict, and the test polls Supabase until COMPLETED.
 *
 * This tests the full orchestrator code path:
 * - HMAC signature verification
 * - Payload parsing and bot identity check (pull_request.user.login == botLogin)
 * - Ticket key extraction from branch ref ([A-Z]+-\d+ regex)
 * - Supabase dedup gate
 * - Cloud Run Job dispatch
 *
 * Branch naming uses `feature/MS-257-e2e-conflict-{id}` so the ticket key regex extracts
 * `MS-257` — no Jira lookup needed since the orchestrator only checks PR author after MS-258.
 *
 * Setup:
 * 1. Sync [E2E_BASE_BRANCH] to main
 * 2. Create feature branch off [E2E_BASE_BRANCH]
 * 3. Push conflicting content to the same file on both branches
 * 4. Open a PR from the feature branch targeting [E2E_BASE_BRANCH]
 *
 * Required env vars: SUPABASE_DB_URL, GCP_PROJECT_ID, GOOGLE_CREDENTIALS_BASE64,
 * ORCHESTRATOR_URL, GITHUB_WEBHOOK_SECRET, GH_TOKEN
 *
 * ⚠️ Dispatches a real Cloud Run Job. Only ever writes to e2e-scratch/ — real code is never affected.
 */
@Tag("e2e")
class ConflictResolutionE2eTest : FullPipelineScenarioBase() {

    override fun scenarioName() = "Conflict Resolution"

    private val shortId = UUID.randomUUID().toString().take(8)
    private val branchName = "feature/MS-257-e2e-conflict-$shortId"
    private val scratchPath = "e2e-scratch/conflict-$shortId.txt"
    private var prNumber: Int = -1

    @BeforeEach
    fun setUpFixture() = runBlocking {
        fixture.syncBranchWithMain(GitHubFixtureClient.E2E_BASE_BRANCH)
        fixture.createBranch(branchName)
        fixture.pushCommit(
            branch = branchName,
            path = scratchPath,
            content = "feature branch content",
            message = "e2e: add scratch file on feature branch"
        )
        fixture.pushCommit(
            branch = GitHubFixtureClient.E2E_BASE_BRANCH,
            path = scratchPath,
            content = "base branch content",
            message = "e2e: add conflicting scratch file on e2e-base"
        )
        prNumber = fixture.openPullRequest(
            branch = branchName,
            title = "[$branchName] E2E conflict resolution fixture",
            body = "Automated E2E fixture PR — safe to close"
        )
    }

    @AfterEach
    fun tearDownFixture() = runBlocking {
        fixture.closePullRequest(prNumber)
        fixture.deleteBranch(branchName)
    }

    @Test
    fun `conflict resolution scenario`() = runBlocking {
        println("\n Starting conflict resolution scenario")
        println(" Branch: $branchName | PR: #$prNumber\n")

        postWebhook("pull_request", dequeueWebhookPayload(prNumber, branchName))
        report.checkpoint("Dequeue webhook accepted by orchestrator", true)

        val dedupKey = "CONFLICT-$prNumber"
        val finalStatus = waitForCompletion(dedupKey, TIMEOUT)
        report.checkpoint(
            "Job reached terminal state (${finalStatus ?: "TIMEOUT"})",
            finalStatus == JobStatus.COMPLETED
        )

        val job = jobRegistry.findLatestJob(dedupKey)
        report.checkpoint("Job COMPLETED in Supabase", job?.status == JobStatus.COMPLETED)

        report.print()
        report.assertAllPassed()
    }

    private fun dequeueWebhookPayload(prNumber: Int, branchName: String) = """
        {
          "action": "dequeued",
          "reason": "merge_conflict",
          "sender": {"login": "github-merge-queue[bot]"},
          "pull_request": {
            "number": $prNumber,
            "head": {"ref": "$branchName"},
            "user": {"login": "media-sage-worker[bot]"}
          }
        }
    """.trimIndent()
}
