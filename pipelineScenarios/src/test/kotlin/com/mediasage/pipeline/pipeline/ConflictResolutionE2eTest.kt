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
 * Full pipeline scenario: conflict resolution.
 *
 * Creates a real merge conflict on GitHub: a feature branch diverges from [GitHubFixtureClient.E2E_BASE_BRANCH]
 * by writing conflicting content to the same file. [GitHubFixtureClient] handles full setup and
 * teardown — no pre-existing PR or branch required.
 *
 * Setup:
 * 1. Sync [E2E_BASE_BRANCH] to main (force-reset discards prior test commits)
 * 2. Create a short-lived feature branch off [E2E_BASE_BRANCH]
 * 3. Push a commit to the feature branch writing content to a scratch file
 * 4. Push a conflicting commit to [E2E_BASE_BRANCH] writing different content to the same file
 * 5. Open a PR from the feature branch targeting [E2E_BASE_BRANCH] — now unmergeable
 *
 * Required env vars: SUPABASE_DB_URL, GCP_PROJECT_ID, GOOGLE_CREDENTIALS_BASE64, GITHUB_TOKEN
 *
 * ⚠️ Dispatches a real Cloud Run Job. Only ever writes to e2e-scratch/ — real code is never affected.
 */
@Tag("e2e")
class ConflictResolutionE2eTest : FullPipelineScenarioBase() {

    override fun scenarioName() = "Conflict Resolution"

    private val shortId = UUID.randomUUID().toString().take(8)
    private val ticketKey = "MS-E2E-${shortId.uppercase()}"
    private val branchName = "e2e/conflict-$shortId"
    private val scratchPath = "e2e-scratch/conflict-$shortId.txt"
    private var prNumber: Int = -1

    @BeforeEach
    fun setUpFixture() = runBlocking {
        fixture.syncBranchWithMain(GitHubFixtureClient.E2E_BASE_BRANCH)
        fixture.createBranch(branchName)
        // Feature branch writes content A — will conflict with base branch content B
        fixture.pushCommit(
            branch = branchName,
            path = scratchPath,
            content = "feature branch content",
            message = "e2e: add scratch file on feature branch"
        )
        // Base branch writes content B to the same file — creates a merge conflict
        fixture.pushCommit(
            branch = GitHubFixtureClient.E2E_BASE_BRANCH,
            path = scratchPath,
            content = "base branch content",
            message = "e2e: add conflicting scratch file on e2e-base"
        )
        prNumber = fixture.openPullRequest(
            branch = branchName,
            title = "[$ticketKey] E2E conflict resolution fixture",
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
        println(" Ticket: $ticketKey | PR: #$prNumber | Branch: $branchName\n")

        val dispatched = service.launchForConflictResolution(ticketKey, prNumber, branchName)
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
