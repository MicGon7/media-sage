package com.mediasage.agentruntime.tools

/**
 * Anthropic tool definitions for the Media Sage agent orchestrator.
 *
 * Each entry describes a named agent behavior triggered by an external event.
 * New agent behaviors should be registered here before routes are wired.
 * This file is the canonical registry of what the agent can do.
 *
 * Pattern: Anthropic orchestrator-worker. The orchestration server (this module)
 * receives webhooks and spawns a Claude Code worker process per ticket/PR.
 * The worker runs autonomously until it exits; the orchestrator deduplicates
 * concurrent calls for the same key.
 */
object ToolDefinitions {

    /**
     * Fires when a Jira ticket with the `autonomous` label enters "To Do" status.
     * The orchestrator spawns a Claude Code agent that executes the full workflow
     * (Jira → branch → code → tests → docs → commit → PR → transitions).
     */
    val JIRA_TICKET_AGENT = AgentTool(
        name = "jira_ticket_agent",
        description = """
            Execute the full autonomous development workflow for a Jira ticket.
            Triggered by a Jira webhook when a ticket labeled 'autonomous' enters 'To Do'.
            The agent reads the ticket description and acceptance criteria, then follows
            the Agent Guidelines in CLAUDE.md to complete the task end-to-end.
        """.trimIndent(),
        inputSchema = InputSchema(
            type = "object",
            properties = mapOf(
                "ticketKey" to Property(
                    type = "string",
                    description = "The Jira ticket key (e.g. MS-136). Used as the deduplication key " +
                        "so only one agent runs per ticket at a time."
                )
            ),
            required = listOf("ticketKey")
        )
    )

    /**
     * Fires when a GitHub PR review comment is left on a PR whose branch contains
     * a ticket key that is labeled `autonomous` in Jira.
     * The agent creates a git worktree, reads the comment, applies the fix,
     * pushes a commit, and replies on the PR.
     */
    val PR_REVIEW_AGENT = AgentTool(
        name = "pr_review_agent",
        description = """
            Respond to a pull request review comment for an autonomous-labeled ticket.
            Triggered by a GitHub webhook (pull_request_review or pull_request_review_comment event).
            The agent checks out the branch in an isolated git worktree, addresses the review
            comment, pushes a fix commit, or replies with a 🤖 Agent: explanation if no code
            change is needed. Approved reviews are ignored.
        """.trimIndent(),
        inputSchema = InputSchema(
            type = "object",
            properties = mapOf(
                "ticketKey" to Property(
                    type = "string",
                    description = "The Jira ticket key extracted from the PR branch name."
                ),
                "prNumber" to Property(
                    type = "integer",
                    description = "GitHub PR number. Used as the deduplication key."
                ),
                "branchRef" to Property(
                    type = "string",
                    description = "The PR head branch ref (e.g. feature/MS-42-fix-something)."
                ),
                "commentBody" to Property(
                    type = "string",
                    description = "The review comment text the agent should address."
                )
            ),
            required = listOf("ticketKey", "prNumber", "branchRef", "commentBody")
        )
    )
}

data class AgentTool(
    val name: String,
    val description: String,
    val inputSchema: InputSchema
)

data class InputSchema(
    val type: String,
    val properties: Map<String, Property>,
    val required: List<String>
)

data class Property(
    val type: String,
    val description: String
)
