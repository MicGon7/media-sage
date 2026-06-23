package com.mediasage.orchestrator.service

/**
 * Dispatches Claude Code agents in response to Jira and GitHub webhook events.
 *
 * Implemented by [AgentLaunchService], which routes each call to a Cloud Run Job.
 * All methods are synchronous: they enqueue a coroutine and return immediately.
 *
 * The orchestrator is a pure dispatcher: it passes only the minimum job identifiers
 * as env vars. The worker's skill fetches all context (ticket content, PR diff, review
 * comments) at runtime — no framing lives in the orchestrator.
 */
interface AgentLauncher {

    /**
     * Dispatches an autonomous Claude Code agent for [ticketKey] as a Cloud Run Job.
     *
     * Deduplicates by ticket key: a second call while a job is already RUNNING or COMPLETED
     * is a no-op. A FAILED or INTERRUPTED job is re-dispatched.
     *
     * @param ticketKey Jira issue key (e.g. "MS-123"). Used as the dedup key and passed to
     *   the worker as `TICKET_KEY`. The worker fetches the ticket content from Jira at runtime.
     * @param dryRun When true, inserts a job row but skips Cloud Run dispatch.
     * @return true if an agent was dispatched; false if deduplicated or Cloud Run is not configured.
     */
    fun launch(ticketKey: String, dryRun: Boolean = false): Boolean

    /**
     * Launches a Cloud Run Job to respond to a PR review.
     *
     * Deduplicates by [prNumber] (`PR-{prNumber}`). The worker derives branch ref,
     * reviewer login, and ticket key from `gh pr view $PR_NUMBER` at runtime.
     *
     * @param prNumber GitHub PR number. Used as the dedup key and passed as `PR_NUMBER`.
     * @return true if dispatched; false if deduplicated or Cloud Run is not configured.
     */
    fun launchForPrReview(prNumber: Int): Boolean

    /**
     * Launches a Cloud Run Job to rebase a branch ejected from the merge queue due to a conflict.
     *
     * Deduplicates by [prNumber] (`CONFLICT-{prNumber}`). The worker derives branch ref
     * and base branch from `gh pr view $PR_NUMBER` at runtime.
     *
     * @param prNumber GitHub PR number. Used as the dedup key and passed as `PR_NUMBER`.
     * @return true if dispatched; false if deduplicated or Cloud Run is not configured.
     */
    fun launchForConflictResolution(prNumber: Int): Boolean

    /**
     * Launches a Cloud Run Job to judge the PR produced by a completed ticket-work job.
     *
     * Deduplicates by ticket key (`JUDGE-{ticketKey}`). The worker fetches the PR diff and
     * ticket AC from GitHub/Jira at runtime.
     *
     * @param ticketKey Jira issue key of the completed ticket-work job.
     * @param prNumber GitHub PR number opened by the worker, passed as `PR_NUMBER`.
     * @return true if dispatched; false if deduplicated or Cloud Run is not configured.
     */
    fun launchForJudge(ticketKey: String, prNumber: Int? = null): Boolean
}
