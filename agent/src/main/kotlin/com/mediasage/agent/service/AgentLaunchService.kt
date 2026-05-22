package com.mediasage.agent.service

import com.mediasage.agent.db.JobStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.util.concurrent.ConcurrentHashMap
import org.slf4j.Logger
import org.slf4j.LoggerFactory

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
class AgentLaunchService(
    private val repoPath: String,
    private val scope: CoroutineScope,
    private val verboseLogging: Boolean = false,
    private val cloudRun: CloudRunDispatch? = null,
    private val jiraCommentPoster: JiraCommentPoster? = null,
    private val agentBriefing: AgentBriefing? = null,
    private val jiraStatusChecker: JiraTicketStatusChecker? = null,
    private val worktreeManager: WorktreeManager = DefaultWorktreeManager(repoPath)
) : AgentLauncher {

    private val log = LoggerFactory.getLogger(AgentLaunchService::class.java)

    // Atomic dedup gate — Set.add() returns false if key already present, in one operation.
    // This prevents the TOCTOU race condition that ConcurrentHashMap.containsKey() + put() would have.
    private val activeKeys: MutableSet<String> = ConcurrentHashMap.newKeySet()

    // Job registry — safe to write after activeKeys.add() succeeds (only one thread reaches this).
    // Stored for future cancellation support.
    private val activeRuns = ConcurrentHashMap<String, Job>()

    override fun launch(ticketKey: String, ticketContent: String?, dryRun: Boolean): Boolean {
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
        if (shouldSkipInterrupted(ticketKey, cloudRun, jiraStatusChecker, log)) return
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
            log.warn("[$ticketKey] dispatch error: ${e.message}")
        }
    }

    // Runs on startup to handle jobs left in RUNNING state by a previous orchestrator instance
    // that crashed or was redeployed. Resumes polling each execution so in-flight work isn't
    // abandoned and the DB doesn't stay permanently stuck in RUNNING.
    suspend fun recoverInterruptedJobs() {
        val cloudRun = cloudRun ?: return
        val runningJobs = cloudRun.jobs.findRunningJobs()
        if (runningJobs.isEmpty()) return
        log.info("Found ${runningJobs.size} interrupted job(s) on startup — recovering")
        runningJobs.forEach { job ->
            val executionName = job.executionName
            if (executionName == null) {
                log.warn("[${job.ticketKey}] RUNNING job ${job.jobId} has no execution name — marking INTERRUPTED")
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
        val worktreeCreated = worktreeManager.createWorktree(worktreePath)
        return spawnAgent(
            key = ticketKey,
            prompt = prompt,
            workDir = if (worktreeCreated) File(worktreePath) else File(repoPath),
            teardown = { if (worktreeCreated) worktreeManager.removeWorktree(worktreePath) }
        )
    }

    /**
     * Launches an agent to respond to a PR review comment for [ticketKey].
     * Creates a git worktree at /tmp/media-sage-pr-{prNumber} so the agent works in
     * isolation and cannot switch branches in the developer's main checkout.
     * De-duplicates by PR number — a second call while one is running is a no-op.
     */
    override fun launchForPrReview(
        ticketKey: String,
        prNumber: Int,
        branchRef: String,
        commentBody: String,
        reviewerLogin: String
    ): Boolean {
        val key = "PR-$prNumber"
        val worktreePath = "/tmp/media-sage-pr-$prNumber"
        val prompt = PR_REVIEW_PROMPT.format(prNumber, ticketKey, commentBody, branchRef)
        val worktreeCreated = worktreeManager.createWorktree(worktreePath, branchRef)
        return spawnAgent(
            key, prompt,
            workDir = if (worktreeCreated) File(worktreePath) else File(repoPath),
            teardown = {
                if (worktreeCreated) worktreeManager.removeWorktree(worktreePath)
                requestReview(prNumber, reviewerLogin, repoPath, log)
            }
        )
    }

    override fun launchForCommentReview(
        ticketKey: String,
        prNumber: Int,
        branchRef: String,
        commentBody: String
    ): Boolean {
        val key = "PR-$prNumber"
        val prompt = PR_COMMENT_REVIEW_PROMPT.format(prNumber, ticketKey, commentBody, branchRef)
        return spawnAgent(key, prompt, workDir = File(repoPath))
    }

    override fun postInlineCommentReply(prNumber: Int) {
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
                log.warn("Failed to post inline comment reply on PR#$prNumber: ${e.message}")
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
                    log.warn("[$key] $line")
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
            val process = worktreeManager.buildAgentProcess(prompt, workDir)
            log.info("Agent launched for $key (pid ${process.pid()}) in ${workDir.path}")
            pipeStreams(key, process)
            // activeKeys.add() succeeded above, so only this thread reaches here.
            // It is safe to write to activeRuns without a race condition.
            val job = scope.launch(Dispatchers.IO) {
                try {
                    val exitCode = process.waitFor()
                    log.info("Agent for $key exited with code $exitCode")
                } finally {
                    // Release the key before teardown so callers polling isActive() see the
                    // release before any teardown side-effects (e.g. countdown latches) fire.
                    activeKeys.remove(key)
                    activeRuns.remove(key)
                    teardown?.invoke()
                }
            }
            activeRuns[key] = job
        } catch (e: Exception) {
            activeKeys.remove(key)
            activeRuns.remove(key)
            log.warn("Failed to launch agent for $key: ${e.message}")
        }
        return true
    }
}

/**
 * Returns true if an INTERRUPTED job should be skipped rather than re-dispatched.
 * Checks Jira ticket status — if the worker finished (In Review/Done) before the
 * orchestrator restarted, we mark the row COMPLETED and skip. If the ticket was
 * manually reset to To Do, we also skip (human must explicitly re-trigger).
 */
private suspend fun shouldSkipInterrupted(
    ticketKey: String,
    cloudRun: CloudRunDispatch,
    checker: JiraTicketStatusChecker?,
    log: Logger
): Boolean {
    if (checker == null) return false
    val latestJob = cloudRun.jobs.findLatestJob(ticketKey) ?: return false
    if (latestJob.status != JobStatus.INTERRUPTED) return false
    val jiraStatus = checker.getTicketStatus(ticketKey)
    return when (jiraStatus) {
        "In Review", "Done" -> {
            log.info("[$ticketKey] INTERRUPTED job, Jira='$jiraStatus' — marking COMPLETED, skipping dispatch")
            cloudRun.jobs.markCompleted(latestJob.jobId)
            true
        }
        "To Do" -> {
            log.info("[$ticketKey] INTERRUPTED job, Jira='To Do' — ticket reset manually, skipping dispatch")
            true
        }
        else -> {
            log.info("[$ticketKey] INTERRUPTED job, Jira='$jiraStatus' — re-dispatching")
            false
        }
    }
}

private suspend fun postInterruptedComment(ticketKey: String, poster: JiraCommentPoster?) {
    poster?.addComment(
        ticketKey,
        "⚠️ The agent job for this ticket was interrupted during an orchestrator restart. " +
            "To re-trigger, move the ticket back to **In Progress**."
    )
}

private fun requestReview(prNumber: Int, reviewerLogin: String, repoPath: String, log: Logger) {
    try {
        ProcessBuilder("gh", "pr", "review-request", prNumber.toString(), "--reviewer", reviewerLogin)
            .directory(java.io.File(repoPath))
            .redirectInput(ProcessBuilder.Redirect.from(java.io.File("/dev/null")))
            .redirectOutput(ProcessBuilder.Redirect.INHERIT)
            .redirectError(ProcessBuilder.Redirect.INHERIT)
            .start()
            .waitFor()
    } catch (e: Exception) {
        log.warn("Failed to re-request review on PR#$prNumber: ${e.message}")
    }
}
