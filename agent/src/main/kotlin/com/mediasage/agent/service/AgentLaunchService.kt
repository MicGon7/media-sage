package com.mediasage.agent.service

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
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
    private val verboseLogging: Boolean = false,
    private val cloudRun: CloudRunDispatch? = null,
    private val jiraCommentPoster: JiraCommentPoster? = null,
    private val agentBriefing: AgentBriefing? = null
) {

    private val log = Logger.getLogger(AgentLaunchService::class.java.name)

    // Atomic dedup gate — Set.add() returns false if key already present, in one operation.
    // This prevents the TOCTOU race condition that ConcurrentHashMap.containsKey() + put() would have.
    private val activeKeys: MutableSet<String> = ConcurrentHashMap.newKeySet()

    // Job registry — safe to write after activeKeys.add() succeeds (only one thread reaches this).
    // Stored for future cancellation support.
    private val activeRuns = ConcurrentHashMap<String, Job>()

    fun launch(ticketKey: String, ticketContent: String? = null, dryRun: Boolean = false): Boolean {
        val basePrompt = if (ticketContent != null) {
            BOOTSTRAP_PROMPT_WITH_CONTENT.format(ticketKey, ticketContent)
        } else {
            BOOTSTRAP_PROMPT_FALLBACK.format(ticketKey)
        }
        return if (cloudRun != null) {
            dispatchToCloudRun(ticketKey, basePrompt, ticketContent, cloudRun, dryRun)
        } else {
            val briefing = if (agentBriefing != null && ticketContent != null) {
                agentBriefing.prepare(ticketKey, ticketContent)
            } else {
                ""
            }
            val prompt = if (briefing.isNotBlank()) "$basePrompt\n\n## Agent Briefing\n$briefing" else basePrompt
            dispatchToLocalProcess(ticketKey, prompt)
        }
    }

    private fun dispatchToCloudRun(
        ticketKey: String,
        basePrompt: String,
        ticketContent: String?,
        cloudRun: CloudRunDispatch,
        dryRun: Boolean = false
    ): Boolean {
        // activeKeys is the synchronous in-process gate. It prevents the TOCTOU race where
        // two concurrent webhooks both pass shouldDispatch() before either inserts a DB row.
        // shouldDispatch() remains the persistent cross-restart gate.
        if (!activeKeys.add(ticketKey)) {
            log.info("[$ticketKey] already in flight — ignoring duplicate webhook")
            return false
        }
        scope.launch {
            try {
                doDispatch(ticketKey, basePrompt, ticketContent, cloudRun, dryRun)
            } finally {
                activeKeys.remove(ticketKey)
            }
        }
        return true
    }

    private suspend fun doDispatch(
        ticketKey: String,
        basePrompt: String,
        ticketContent: String?,
        cloudRun: CloudRunDispatch,
        dryRun: Boolean
    ) {
        if (!cloudRun.jobs.shouldDispatch(ticketKey)) {
            log.info("[$ticketKey] job already running or completed — ignoring duplicate webhook")
            return
        }
        // Dedup passed — safe to run AgentBriefing now (costs tokens, must not run on duplicates).
        val briefing = if (agentBriefing != null && ticketContent != null) {
            agentBriefing.prepare(ticketKey, ticketContent)
        } else { "" }
        val prompt = if (briefing.isNotBlank()) "$basePrompt\n\n## Agent Briefing\n$briefing" else basePrompt
        val jobId = cloudRun.jobs.insert(ticketKey, prompt)
        if (dryRun) {
            log.info("[$ticketKey] dry-run: job $jobId inserted — skipping Cloud Run dispatch")
            cloudRun.jobs.markFailed(jobId)
            return
        }
        log.info("[$ticketKey] job $jobId inserted — dispatching to Cloud Run")
        try {
            cloudRun.dispatcher.executeJob(jobId, ticketKey, prompt)
        } catch (e: Exception) {
            cloudRun.jobs.markFailed(jobId)
            log.warning("[$ticketKey] dispatch error: ${e.message}")
        }
    }


    suspend fun recoverInterruptedJobs() {
        val cloudRun = cloudRun ?: return
        val runningJobs = cloudRun.jobs.findRunningJobs()
        if (runningJobs.isEmpty()) return
        log.info("Found ${runningJobs.size} interrupted job(s) on startup — recovering")
        runningJobs.forEach { job ->
            val executionName = job.executionName
            if (executionName == null) {
                log.warning("[${job.ticketKey}] RUNNING job ${job.jobId} has no execution name — marking INTERRUPTED")
                cloudRun.jobs.markInterrupted(job.jobId)
                postInterruptedComment(job.ticketKey, jiraCommentPoster)
                return@forEach
            }
            scope.launch {
                val recovered = cloudRun.dispatcher.recoverJob(job.jobId, job.ticketKey, executionName)
                if (!recovered) postInterruptedComment(job.ticketKey, jiraCommentPoster)
            }
        }
    }

    private fun dispatchToLocalProcess(ticketKey: String, prompt: String): Boolean {
        // Each Jira ticket gets an isolated worktree so concurrent agents can't corrupt
        // each other's git state. We use --no-checkout because the agent creates its own
        // branch as its first action — there is no existing branch to check out yet.
        val worktreePath = "${repoPath}-worktrees/$ticketKey"
        val worktreeCreated = createWorktree(worktreePath)
        return spawnAgent(
            key = ticketKey,
            prompt = prompt,
            workDir = if (worktreeCreated) File(worktreePath) else File(repoPath),
            teardown = { if (worktreeCreated) removeWorktree(worktreePath) }
        )
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
                requestReview(prNumber, reviewerLogin, repoPath, log)
            }
        )
    }

    /**
     * Creates a git worktree at [path].
     *
     * When [branchRef] is provided (PR review flow), the worktree checks out that existing branch.
     * When [branchRef] is null (Jira ticket flow), --no-checkout is used — the agent creates
     * its own branch as its first action, so there is nothing to check out yet.
     *
     * Returns true if the worktree was created successfully, false otherwise.
     * On failure the caller falls back to running the agent in repoPath.
     */
    protected open fun createWorktree(path: String, branchRef: String? = null): Boolean = try {
        val args = if (branchRef != null) {
            listOf("git", "worktree", "add", path, branchRef)
        } else {
            listOf("git", "worktree", "add", "--no-checkout", path)
        }
        val exitCode = ProcessBuilder(args)
            .directory(File(repoPath))
            .redirectError(ProcessBuilder.Redirect.INHERIT)
            .start()
            .waitFor()
        if (exitCode != 0) log.warning("Worktree creation failed at $path — running agent in repoPath.")
        exitCode == 0
    } catch (e: Exception) {
        log.warning("Worktree creation failed at $path: ${e.message}. Running agent in repoPath.")
        false
    }

    protected open fun removeWorktree(path: String) {
        ProcessBuilder("git", "worktree", "remove", "--force", path)
            .directory(File(repoPath))
            .start()
            .waitFor()
    }

    /**
     * Builds and starts the agent process. Protected open so tests can substitute a
     * controllable fake process without needing a real `claude` binary on the PATH.
     *
     * This follows the Template Method pattern: the algorithm lives in [spawnAgent],
     * the specific command is the overridable implementation detail.
     */
    protected open fun buildAgentProcess(prompt: String, workDir: File): Process =
        ProcessBuilder(claudeCommand(prompt))
            .directory(workDir)
            .redirectInput(ProcessBuilder.Redirect.from(File("/dev/null")))
            .start()

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
        // activeKeys.add() is the atomic gate. If it returns false, the key was already
        // present — another agent is running for this ticket. Ignore the duplicate.
        if (!activeKeys.add(key)) {
            log.info("[$key] already in flight — ignoring duplicate webhook")
            return false
        }
        try {
            val process = buildAgentProcess(prompt, workDir)
            log.info("Agent launched for $key (pid ${process.pid()}) in ${workDir.path}")
            pipeStreams(key, process)
            // activeKeys.add() succeeded above, so only this thread reaches here.
            // It is safe to write to activeRuns without a race condition.
            val job = scope.launch(Dispatchers.IO) {
                try {
                    val exitCode = process.waitFor()
                    log.info("Agent for $key exited with code $exitCode")
                } finally {
                    teardown?.invoke()
                    activeKeys.remove(key)
                    activeRuns.remove(key)
                }
            }
            activeRuns[key] = job
        } catch (e: Exception) {
            activeKeys.remove(key)
            activeRuns.remove(key)
            log.warning("Failed to launch agent for $key: ${e.message}")
        }
        return true
    }
}

private suspend fun postInterruptedComment(ticketKey: String, poster: JiraCommentPoster?) {
    poster?.addComment(
        ticketKey,
        "⚠️ The agent job for this ticket was interrupted during an orchestrator restart. " +
            "To re-trigger, move the ticket back to **In Progress**."
    )
}

private fun requestReview(prNumber: Int, reviewerLogin: String, repoPath: String, log: java.util.logging.Logger) {
    try {
        ProcessBuilder("gh", "pr", "review-request", prNumber.toString(), "--reviewer", reviewerLogin)
            .directory(java.io.File(repoPath))
            .redirectInput(ProcessBuilder.Redirect.from(java.io.File("/dev/null")))
            .redirectOutput(ProcessBuilder.Redirect.INHERIT)
            .redirectError(ProcessBuilder.Redirect.INHERIT)
            .start()
            .waitFor()
    } catch (e: Exception) {
        log.warning("Failed to re-request review on PR#$prNumber: ${e.message}")
    }
}

private fun claudeCommand(prompt: String) = listOf(
    "claude", "-p", prompt,
    "--dangerously-skip-permissions",
    "--output-format", "stream-json",
    "--verbose"
)
