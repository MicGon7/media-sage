package com.mediasage.agent.service

/**
 * Dispatches Claude Code agents in response to Jira and GitHub webhook events.
 *
 * Implemented by [AgentLaunchService], which routes each call to a Cloud Run Job.
 * All methods are synchronous: they enqueue a coroutine and return immediately.
 */
interface AgentLauncher {

    /**
     * Dispatches an autonomous Claude Code agent for [ticketKey] as a Cloud Run Job.
     *
     * Deduplicates by ticket key: a second call while a job is already RUNNING or COMPLETED
     * is a no-op. A FAILED or INTERRUPTED job is re-dispatched.
     *
     * @param ticketKey Jira issue key (e.g. "MS-123"). Used as the dedup key and injected into
     *   the agent bootstrap prompt.
     * @param ticketContent Raw ticket text used to build the bootstrap prompt. Pass null to
     *   fall back to a prompt that instructs the agent to fetch the ticket from Jira itself.
     * @param dryRun When true, inserts a job row but skips Cloud Run dispatch. Useful for
     *   testing the dedup and DB path without incurring compute cost.
     * @return true if an agent was dispatched; false if the call was deduplicated or Cloud Run
     *   is not configured.
     */
    fun launch(ticketKey: String, ticketContent: String? = null, dryRun: Boolean = false): Boolean

    /**
     * Launches a Cloud Run Job to respond to a formal PR review ([reviewerLogin] requested changes).
     *
     * The worker checks out [branchRef], applies the fix, pushes a commit, and then calls
     * `gh pr review-request` to re-request review from [reviewerLogin]. Deduplicates by
     * [prNumber] — a second call while one job is running is a no-op.
     *
     * @param ticketKey Jira issue key included in the agent prompt for context.
     * @param prNumber GitHub PR number. Used as the dedup key and passed to `gh` CLI commands.
     * @param branchRef Branch the worker should check out (e.g. "feature/MS-123-...").
     * @param commentBody Text of the review comment forwarded to the agent as context.
     * @param reviewerLogin GitHub login of the reviewer to re-request once the fix is pushed.
     * @return true if dispatched; false if deduplicated or Cloud Run is not configured.
     */
    fun launchForPrReview(
        ticketKey: String,
        prNumber: Int,
        branchRef: String,
        commentBody: String,
        reviewerLogin: String
    ): Boolean

    /**
     * Launches a Cloud Run Job to answer a PR comment review (not a formal changes-requested review).
     *
     * Unlike [launchForPrReview], the worker posts a reply comment only — it does **not** push
     * code changes or re-request review. Deduplicates by [prNumber].
     *
     * @param ticketKey Jira issue key included in the agent prompt for context.
     * @param prNumber GitHub PR number. Used as the dedup key and passed to `gh pr comment`.
     * @param branchRef Branch the worker reads for context (no commits are pushed).
     * @param commentBody Text of the review comment the agent should respond to.
     * @return true if dispatched; false if deduplicated or Cloud Run is not configured.
     */
    fun launchForCommentReview(
        ticketKey: String,
        prNumber: Int,
        branchRef: String,
        commentBody: String
    ): Boolean

    /**
     * Posts a nudge comment on PR [prNumber] asking the reviewer to submit a formal
     * **Changes requested** review rather than leaving standalone inline comments.
     *
     * This lets the agent batch all feedback into a single Cloud Run Job response instead
     * of firing one job per inline comment thread.
     */
    fun postInlineCommentReply(prNumber: Int)
}
