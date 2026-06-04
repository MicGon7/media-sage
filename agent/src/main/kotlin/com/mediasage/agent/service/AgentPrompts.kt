package com.mediasage.agent.service

/**
 * Bootstrap prompt templates for each job type dispatched by [AgentLaunchService].
 *
 * Each prompt supplies job-specific context (ticket key, PR number, branch, comment text).
 * The skill invocation at the end of each prompt tells the worker which instruction set to follow.
 *
 * Three-part model:
 * - CLAUDE.md → rules  (standing constraints across all jobs)
 * - Prompt    → context  (what the job is)
 * - Skill     → instructions  (how to execute it)
 */

internal val ticketWorkPrompt = """
    Your assigned ticket is %s.

    ## Ticket
    %s

    Follow the Agent Guidelines in CLAUDE.md to execute the full autonomous workflow. /ticket-work
""".trimIndent()

internal val ticketWorkFallbackPrompt = """
    Your assigned ticket is %s. Retrieve it from Jira (cloudId: media-sage.atlassian.net),
    read the description and acceptance criteria, then follow the Agent Guidelines in CLAUDE.md
    to execute the full autonomous workflow. /ticket-work
""".trimIndent()

internal val prReviewPrompt = """
    PR #%d for ticket %s has a new review comment: "%s".
    Branch: %s. Reviewer: %s.

    /pr-review
""".trimIndent()

internal val conflictResolutionPrompt = """
    PR #%d for ticket %s: branch %s was ejected from the merge queue due to a conflict with %s.

    /conflict-resolution
""".trimIndent()

internal val prCommentPrompt = """
    PR #%d for ticket %s has a new comment: "%s".
    Branch: %s.

    /pr-comment
""".trimIndent()
