package com.mediasage.agentruntime.service

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
     * Launches a Cloud Run Job for [ticketKey] after its last blocker merged.
     *
     * Posts a Jira comment on [ticketKey] citing [blockerKey] as the trigger before
     * dispatching. Deduplicates by ticket key — the resulting bot-initiated In Progress
     * Jira webhook is caught by the in-process [activeKeys] gate in [AgentLaunchService],
     * preventing a second dispatch.
     *
     * @param ticketKey Jira issue key of the newly unblocked ticket (e.g. "MS-521").
     * @param blockerKey Jira issue key of the blocker whose PR just merged (e.g. "MS-520").
     * @return true if dispatched; false if deduplicated or Cloud Run is not configured.
     */
    fun launchForUnblockedTicket(ticketKey: String, blockerKey: String): Boolean

}
