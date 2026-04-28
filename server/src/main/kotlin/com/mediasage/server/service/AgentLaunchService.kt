package com.mediasage.server.service

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.logging.Logger

private const val BOOTSTRAP_PROMPT =
    "Your assigned ticket is %s. Retrieve it from Jira (cloudId: media-sage.atlassian.net), " +
    "read the description and acceptance criteria, then follow the Agent Guidelines in CLAUDE.md " +
    "to execute the full autonomous workflow."

private const val PR_REVIEW_PROMPT =
    "PR #%d for ticket %s has a new review comment: \"%s\". " +
    "Check out branch %s, read the relevant source files, and make the necessary change. " +
    "Then push a fix commit. If no code change is needed, reply with " +
    "'🤖 **Agent:**' explaining your decision. " +
    "Follow the Agent Guidelines in CLAUDE.md."

/**
 * Spawns autonomous Claude Code agents.
 * Guards against double-firing: a second launch call for the same key is a no-op
 * until the first agent process exits.
 */
class AgentLaunchService(
    private val repoPath: String,
    private val scope: CoroutineScope
) {

    private val log = Logger.getLogger(AgentLaunchService::class.java.name)
    private val activeKeys: MutableSet<String> = ConcurrentHashMap.newKeySet()

    /**
     * Launches an autonomous agent for [ticketKey].
     * Returns true if the agent was spawned, false if one is already running for this ticket.
     */
    fun launch(ticketKey: String): Boolean =
        spawnAgent(ticketKey, BOOTSTRAP_PROMPT.format(ticketKey))

    /**
     * Launches an agent to respond to a PR review comment for [ticketKey].
     * Creates a git worktree at /tmp/media-sage-pr-{prNumber} so the agent works in
     * isolation and cannot switch branches in the developer's main checkout.
     * De-duplicates by PR number — a second call while one is running is a no-op.
     */
    fun launchForPrReview(ticketKey: String, prNumber: Int, branchRef: String, commentBody: String): Boolean {
        val key = "PR-$prNumber"
        val worktreePath = "/tmp/media-sage-pr-$prNumber"
        val prompt = PR_REVIEW_PROMPT.format(prNumber, ticketKey, commentBody, branchRef)
        val worktreeFile = File(worktreePath)
        val exitCode = ProcessBuilder("git", "worktree", "add", worktreePath, branchRef)
            .directory(File(repoPath))
            .redirectError(ProcessBuilder.Redirect.INHERIT)
            .start()
            .waitFor()
        val worktreeCreated = exitCode == 0

        if (!worktreeCreated) {
            log.warning("Worktree creation failed for $branchRef — branch likely checked out in main repo. Running agent in repoPath.")
        }

        return spawnAgent(
            key, prompt,
            workDir = if (worktreeCreated) worktreeFile else File(repoPath),
            teardown = if (worktreeCreated) ({
                ProcessBuilder("git", "worktree", "remove", "--force", worktreePath)
                    .directory(File(repoPath))
                    .start()
                    .waitFor()
            }) else null
        )
    }

    fun isActive(key: String): Boolean = key in activeKeys

    private fun spawnAgent(
        key: String,
        prompt: String,
        workDir: File = File(repoPath),
        teardown: (() -> Unit)? = null
    ): Boolean {
        if (!activeKeys.add(key)) return false

        val command = listOf("claude", "-p", prompt, "--dangerously-skip-permissions")

        try {
            val process = ProcessBuilder(command)
                .directory(workDir)
                .redirectOutput(ProcessBuilder.Redirect.INHERIT)
                .redirectError(ProcessBuilder.Redirect.INHERIT)
                .start()

            log.info("Agent launched for $key (pid ${process.pid()}) prompt: $prompt")

            scope.launch(Dispatchers.IO) {
                try {
                    val exitCode = process.waitFor()
                    log.info("Agent for $key exited with code $exitCode")
                } finally {
                    teardown?.invoke()
                    activeKeys.remove(key)
                }
            }
        } catch (e: Exception) {
            activeKeys.remove(key)
            log.warning("Failed to launch agent for $key: ${e.message}")
        }

        return true
    }
}
