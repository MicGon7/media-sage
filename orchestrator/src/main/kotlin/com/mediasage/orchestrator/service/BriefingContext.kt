package com.mediasage.orchestrator.service

/**
 * Typed context passed to [BriefingService] before each worker dispatch.
 *
 * Each subtype carries exactly the information available at dispatch time for that scenario —
 * no nullable fields, no shared mutable state. [BriefingService] pattern-matches on the subtype
 * to build a scenario-appropriate Haiku prompt.
 *
 * Adding a new dispatch scenario means adding a subtype here and a corresponding branch in
 * [BriefingService.brief].
 */
sealed class BriefingContext {

    /**
     * A new ticket assigned to the bot — feature work, bug fix, or infrastructure change.
     *
     * @param ticketKey Jira issue key (e.g. "MS-123").
     * @param ticketContent Raw ticket text including description and acceptance criteria.
     */
    data class TicketWork(
        val ticketKey: String,
        val ticketContent: String,
    ) : BriefingContext()

    /**
     * A branch ejected from the merge queue due to a conflict with the base branch.
     *
     * @param ticketKey Jira issue key for context.
     * @param prNumber GitHub PR number.
     * @param branchRef Branch that was ejected.
     * @param baseBranch Branch to rebase onto (typically "main").
     */
    data class ConflictResolution(
        val ticketKey: String,
        val prNumber: Int,
        val branchRef: String,
        val baseBranch: String,
    ) : BriefingContext()
}
