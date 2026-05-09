package com.mediasage.agent.service

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.util.concurrent.ConcurrentHashMap
import java.util.logging.Logger

private const val BOOTSTRAP_PROMPT_WITH_CONTENT =
    "Your assigned ticket is %s.\n\n## Ticket\n%s\n\n" +
    "Follow the Agent Guidelines in CLAUDE.md to execute the full autonomous workflow."

private const val BOOTSTRAP_PROMPT_FALLBACK =
    "Your assigned ticket is %s. Retrieve it from Jira (cloudId: media-sage.atlassian.net), " +
    "read the description and acceptance criteria, then follow the Agent Guidelines in CLAUDE.md " +
    "to execute the full autonomous workflow."

private const val PR_REVIEW_PROMPT =
    "PR #%1\$d for ticket %2\$s has a new review comment: \"%3\$s\". " +
    "Check out branch %4\$s, read the relevant source files, and make the necessary change. " +
    "Then push a fix commit. If no code change is needed, post a comment on the PR using " +
    "`gh pr comment %1\$d --body '🤖 **Agent:** your explanation here'` and exit. " +
    "Follow the Agent Guidelines in CLAUDE.md."

private const val PR_COMMENT_REVIEW_PROMPT =
    "PR #%1\$d for ticket %2\$s has a new comment review: \"%3\$s\". " +
    "Read the relevant source files on branch %4\$s to understand the context, then answer the " +
    "reviewer's questions by posting a PR comment: " +
    "`gh pr comment %1\$d --body '🤖 **Agent:** your answer here'`. " +
    "Do NOT push any code changes. Follow the Agent Guidelines in CLAUDE.md."

/**
 * Spawns autonomous Claude Code agents.
 * Guards against double-firing: a second launch call for the same key is a no-op
 * until the first agent process exits.
 */
open class AgentLaunchService(
    private val repoPath: String,
    private val scope: CoroutineScope,
    private val verboseLogging: Boolean = false
) {

    private val log = Logger.getLogger(AgentLaunchService::class.java.name)
    private val activeKeys: MutableSet<String> = ConcurrentHashMap.newKeySet()

    fun launch(ticketKey: String, ticketContent: String? = null): Boolean {
        val prompt = if (ticketContent != null) {
            BOOTSTRAP_PROMPT_WITH_CONTENT.format(ticketKey, ticketContent)
        } else {
            BOOTSTRAP_PROMPT_FALLBACK.format(ticketKey)
        }
        return spawnAgent(ticketKey, prompt)
    }

    /**
     * Launches an agent to respond to a PR review comment for [ticketKey].
     * Creates a git worktree at /tmp/media-sage-pr-{prNumber} so the agent works in
     * isolation and cannot switch branches in the developer's main checkout.
     * De-duplicates by PR number — a second call while one is running is a no-op.
     */
    open fun launchForPrReview(ticketKey: String, prNumber: Int, branchRef: String, commentBody: String, reviewerLogin: String): Boolean {
        val key = "PR-$prNumber"
        val worktreePath = "/tmp/media-sage-pr-$prNumber"
        val prompt = PR_REVIEW_PROMPT.format(prNumber, ticketKey, commentBody, branchRef)
        val worktreeCreated = createWorktree(worktreePath, branchRef)
        return spawnAgent(
            key, prompt,
            workDir = if (worktreeCreated) File(worktreePath) else File(repoPath),
            teardown = {
                if (worktreeCreated) removeWorktree(worktreePath)
                requestReview(prNumber, reviewerLogin)
            }
        )
    }

    private fun requestReview(prNumber: Int, reviewerLogin: String) {
        try {
            ProcessBuilder("gh", "pr", "review-request", prNumber.toString(), "--reviewer", reviewerLogin)
                .directory(File(repoPath))
                .redirectInput(ProcessBuilder.Redirect.from(File("/dev/null")))
                .redirectOutput(ProcessBuilder.Redirect.INHERIT)
                .redirectError(ProcessBuilder.Redirect.INHERIT)
                .start()
                .waitFor()
        } catch (e: Exception) {
            log.warning("Failed to re-request review on PR#$prNumber: ${e.message}")
        }
    }

    private fun createWorktree(path: String, branchRef: String): Boolean = try {
        val exitCode = ProcessBuilder("git", "worktree", "add", path, branchRef)
            .directory(File(repoPath))
            .redirectError(ProcessBuilder.Redirect.INHERIT)
            .start()
            .waitFor()
        if (exitCode != 0) log.warning("Worktree creation failed for $branchRef — running agent in repoPath.")
        exitCode == 0
    } catch (e: Exception) {
        log.warning("Worktree creation failed for $branchRef: ${e.message}. Running agent in repoPath.")
        false
    }

    private fun removeWorktree(path: String) {
        ProcessBuilder("git", "worktree", "remove", "--force", path)
            .directory(File(repoPath))
            .start()
            .waitFor()
    }

    open fun launchForCommentReview(ticketKey: String, prNumber: Int, branchRef: String, commentBody: String): Boolean {
        val key = "PR-$prNumber"
        val prompt = PR_COMMENT_REVIEW_PROMPT.format(prNumber, ticketKey, commentBody, branchRef)
        return spawnAgent(key, prompt, workDir = File(repoPath))
    }

    open fun postInlineCommentReply(prNumber: Int) {
        val body = "🤖 **Agent:** I noticed your inline comment. Please submit a formal review " +
            "with **Changes requested** and I'll address all your feedback in one pass."
        scope.launch(Dispatchers.IO) {
            try {
                ProcessBuilder("gh", "pr", "comment", prNumber.toString(), "--body", body)
                    .directory(File(repoPath))
                    .redirectInput(ProcessBuilder.Redirect.from(File("/dev/null")))
                    .redirectOutput(ProcessBuilder.Redirect.INHERIT)
                    .redirectError(ProcessBuilder.Redirect.INHERIT)
                    .start()
                    .waitFor()
            } catch (e: Exception) {
                log.warning("Failed to post inline comment reply on PR#$prNumber: ${e.message}")
            }
        }
    }

    fun isActive(key: String): Boolean = key in activeKeys

    private fun claudeCommand(prompt: String) = listOf(
        "claude", "-p", prompt,
        "--dangerously-skip-permissions",
        "--output-format", "stream-json",
        "--verbose"
    )

    private fun pipeStreams(key: String, process: Process) {
        scope.launch(Dispatchers.IO) {
            BufferedReader(InputStreamReader(process.inputStream)).forEachLine { line ->
                if (verboseLogging) {
                    log.info("[$key] $line")
                } else {
                    parseStreamJsonMilestone(line)?.let { milestone ->
                        milestone.lines().forEach { log.info("[$key] $it") }
                    }
                }
            }
        }
        scope.launch(Dispatchers.IO) {
            BufferedReader(InputStreamReader(process.errorStream)).forEachLine { line ->
                val milestone = parseStreamJsonMilestone(line)
                if (milestone != null) {
                    milestone.lines().forEach { log.info("[$key] $it") }
                } else {
                    log.warning("[$key] $line")
                }
            }
        }
    }

    private fun spawnAgent(
        key: String,
        prompt: String,
        workDir: File = File(repoPath),
        teardown: (() -> Unit)? = null
    ): Boolean {
        if (!activeKeys.add(key)) return false
        try {
            val process = ProcessBuilder(claudeCommand(prompt))
                .directory(workDir)
                .redirectInput(ProcessBuilder.Redirect.from(File("/dev/null")))
                .start()
            log.info("Agent launched for $key (pid ${process.pid()}) prompt: $prompt")
            pipeStreams(key, process)
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
