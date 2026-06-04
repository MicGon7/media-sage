package com.mediasage.agent.service

import com.mediasage.agent.db.JobStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap
import org.slf4j.Logger
import org.slf4j.LoggerFactory

private const val BOOTSTRAP_PROMPT_WITH_CONTENT =
    "Your assigned ticket is %s.\n\n## Ticket\n%s\n\n" +
    "Follow the Agent Guidelines in CLAUDE.md to execute the full autonomous workflow. /ticket-work"

private const val BOOTSTRAP_PROMPT_FALLBACK =
    "Your assigned ticket is %s. Retrieve it from Jira (cloudId: media-sage.atlassian.net), " +
    "read the description and acceptance criteria, then follow the Agent Guidelines in CLAUDE.md " +
    "to execute the full autonomous workflow. /ticket-work"

private const val PR_REVIEW_PROMPT =
    "PR #%1\$d for ticket %2\$s has a new review comment: \"%3\$s\". " +
    "Branch: %4\$s. Reviewer: %5\$s.\n\n/pr-review"

private const val CONFLICT_RESOLUTION_PROMPT =
    "Branch %3\$s for ticket %2\$s was ejected from the merge queue due to a conflict with %4\$s. " +
    "PR #%1\$d.\n\n/conflict-resolution"

private const val PR_COMMENT_REVIEW_PROMPT =
    "PR #%1\$d for ticket %2\$s has a new comment: \"%3\$s\". " +
    "Branch: %4\$s.\n\n/pr-comment"

/**
 * Dispatches autonomous Claude Code agents via Cloud Run Jobs.
 * Guards against double-firing: a second launch call for the same key is a no-op
 * until the first dispatch coroutine completes.
 *
 * All agent work — ticket implementation and PR review — is dispatched as Cloud Run Jobs.
 * The orchestrator is a pure event router: it receives webhooks, builds prompts, and
 * dispatches jobs. No agent processes run locally.
 */
class AgentLaunchService(
    private val scope: CoroutineScope,
    internal val cloudRun: CloudRunDispatch? = null,
    private val jiraCommentPoster: JiraCommentPoster? = null,
    private val jiraStatusChecker: JiraTicketStatusChecker? = null,
    private val briefingService: BriefingService? = null,
) : AgentLauncher {

    private val log = LoggerFactory.getLogger(AgentLaunchService::class.java)

    // Atomic dedup gate — Set.add() returns false if key already present, in one operation.
    // This prevents the TOCTOU race condition that ConcurrentHashMap.containsKey() + put() would have.
    private val activeKeys: MutableSet<String> = ConcurrentHashMap.newKeySet()

    /**
     * Launches an autonomous Claude Code agent for [ticketKey] via Cloud Run Jobs.
     *
     * **Dedup policy:** A second persistent gate checks the job registry:
     * - RUNNING or COMPLETED → skip (concurrent duplicate or already finished)
     * - FAILED or INTERRUPTED → re-dispatch (retry eligible)
     *
     * The in-process [activeKeys] gate is always evaluated first to prevent the TOCTOU race
     * between reading and writing the persistent DB row.
     *
     * @param ticketKey Jira issue key (e.g. "MS-123"). Used as the dedup key and forwarded to the agent.
     * @param ticketContent Raw ticket text from Jira used to build the bootstrap prompt.
     *   Pass null to fall back to a prompt that instructs the agent to fetch the ticket itself.
     * @param dryRun If true, inserts a job row but skips execution dispatch.
     * @return true if an agent was dispatched; false if the call was deduplicated or Cloud Run is not configured.
     */
    override fun launch(ticketKey: String, ticketContent: String?, dryRun: Boolean): Boolean {
        val cloudRun = cloudRun ?: return false
        if (ticketContent != null && !ticketContent.contains("relevant files", ignoreCase = true)) {
            log.warn("[$ticketKey] ticket is missing a Relevant files section — worker will start without file guidance")
        }
        val basePrompt = if (ticketContent != null) {
            BOOTSTRAP_PROMPT_WITH_CONTENT.format(ticketKey, ticketContent)
        } else {
            BOOTSTRAP_PROMPT_FALLBACK.format(ticketKey)
        }
        val context = ticketContent?.let {
            BriefingContext.TicketWork(ticketKey, it)
        }
        return dispatchToCloudRun(ticketKey, basePrompt, cloudRun, dryRun, briefingContext = context)
    }

    private fun dispatchToCloudRun(
        ticketKey: String,
        prompt: String,
        cloudRun: CloudRunDispatch,
        dryRun: Boolean = false,
        jiraTicketKey: String? = null,
        briefingContext: BriefingContext? = null,
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
                doDispatch(ticketKey, prompt, cloudRun, dryRun, jiraTicketKey, briefingContext)
            } finally {
                activeKeys.remove(ticketKey)
            }
        }
        return true
    }

    private suspend fun doDispatch(
        ticketKey: String,
        basePrompt: String,
        cloudRun: CloudRunDispatch,
        dryRun: Boolean,
        jiraTicketKey: String? = null,
        briefingContext: BriefingContext? = null,
    ) {
        if (!cloudRun.jobs.shouldDispatch(ticketKey)) {
            log.info("[$ticketKey] job already running or completed — ignoring duplicate webhook")
            return
        }
        if (shouldSkipInterrupted(ticketKey, cloudRun, jiraStatusChecker, log)) return
        val prompt = buildPromptWithBriefing(ticketKey, basePrompt, briefingContext)
        val jobId = cloudRun.jobs.insert(ticketKey, prompt)
        if (dryRun) {
            log.info("[$ticketKey] dry-run: job $jobId inserted — skipping Cloud Run dispatch")
            cloudRun.jobs.markFailed(jobId)
            return
        }
        log.info("[$ticketKey] job $jobId inserted — dispatching to Cloud Run")
        try {
            cloudRun.dispatcher.executeJob(jobId, ticketKey, prompt, jiraTicketKey)
        } catch (e: Exception) {
            cloudRun.jobs.markFailed(jobId)
            log.warn("[$ticketKey] dispatch error: ${e.message}")
        }
    }

    /**
     * Recovers jobs left in RUNNING state after an orchestrator restart.
     *
     * Called once at startup by [Application]. Queries the job registry for all RUNNING rows and,
     * for each, asks [JobDispatcher.recoverJob] to check whether the backing Cloud Run execution
     * is still alive. If the execution is gone the job is marked INTERRUPTED and a Jira comment
     * is posted instructing the team to re-trigger manually. No-op when Cloud Run is not configured.
     */
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

    /**
     * Launches a Cloud Run Job to respond to a PR review comment for [ticketKey].
     * The worker checks out [branchRef], makes the necessary change, pushes a fix commit,
     * then re-requests review from [reviewerLogin] via `gh pr review-request`.
     * De-duplicates by PR number — a second call while one is running is a no-op.
     *
     * @param ticketKey Jira issue key forwarded to the worker for context (e.g. "MS-123").
     * @param prNumber GitHub PR number used as the dedup key and for `gh pr` commands.
     * @param branchRef Branch the PR targets; checked out by the worker to make the fix.
     * @param commentBody Text of the reviewer's comment forwarded verbatim to the worker prompt.
     * @param reviewerLogin GitHub login of the reviewer to re-request review from after the fix.
     * @return true if a job was dispatched; false if deduplicated or Cloud Run is not configured.
     */
    override fun launchForPrReview(
        ticketKey: String,
        prNumber: Int,
        branchRef: String,
        commentBody: String,
        reviewerLogin: String
    ): Boolean {
        val cloudRun = cloudRun ?: return false
        val key = "PR-$prNumber"
        val basePrompt = PR_REVIEW_PROMPT.format(prNumber, ticketKey, commentBody, branchRef, reviewerLogin)
        return dispatchToCloudRun(key, basePrompt, cloudRun, jiraTicketKey = ticketKey)
    }

    /**
     * Launches a Cloud Run Job to answer reviewer questions posted as a PR comment (not a formal
     * changes-requested review). The worker reads the branch context and replies via `gh pr comment`
     * but does **not** push any code changes.
     * De-duplicates by PR number — a second call while one is running is a no-op.
     *
     * @param ticketKey Jira issue key for context forwarded to the worker.
     * @param prNumber GitHub PR number.
     * @param branchRef Branch the PR targets, checked out by the worker for context.
     * @param commentBody Text of the reviewer's comment.
     * @return true if a job was dispatched; false if deduplicated or Cloud Run is not configured.
     */
    override fun launchForCommentReview(
        ticketKey: String,
        prNumber: Int,
        branchRef: String,
        commentBody: String
    ): Boolean {
        val cloudRun = cloudRun ?: return false
        val key = "PR-$prNumber"
        val basePrompt = PR_COMMENT_REVIEW_PROMPT.format(prNumber, ticketKey, commentBody, branchRef)
        val context = BriefingContext.CommentReview(ticketKey, prNumber, commentBody)
        return dispatchToCloudRun(key, basePrompt, cloudRun, jiraTicketKey = ticketKey, briefingContext = context)
    }

    /**
     * Launches a Cloud Run Job to rebase a branch ejected from the merge queue due to a conflict.
     * De-duplicates by PR number using the key `CONFLICT-{prNumber}`.
     *
     * @param ticketKey Jira issue key forwarded to the worker for context (e.g. "MS-123").
     * @param prNumber GitHub PR number used as the dedup key.
     * @param branchRef The feature branch that was ejected from the merge queue.
     * @param baseBranch The base branch the feature branch conflicted with (e.g. "main").
     * @return true if a job was dispatched; false if deduplicated or Cloud Run is not configured.
     */
    override fun launchForConflictResolution(
        ticketKey: String,
        prNumber: Int,
        branchRef: String,
        baseBranch: String
    ): Boolean {
        val cloudRun = cloudRun ?: return false
        val key = "CONFLICT-$prNumber"
        val basePrompt = CONFLICT_RESOLUTION_PROMPT.format(prNumber, ticketKey, branchRef, baseBranch)
        val context = BriefingContext.ConflictResolution(ticketKey, prNumber, branchRef, baseBranch)
        return dispatchToCloudRun(key, basePrompt, cloudRun, jiraTicketKey = ticketKey, briefingContext = context)
    }

    /**
     * Returns true if [key] currently has a dispatch coroutine in flight.
     * Used in tests to assert that a dispatch was started without waiting for it to complete.
     *
     * @param key Dedup key — a Jira ticket key (e.g. "MS-123") for ticket launches,
     *   or a derived key like "PR-456" or "CONFLICT-456" for PR-driven launches.
     */
    fun isActive(key: String): Boolean = key in activeKeys

    private suspend fun buildPromptWithBriefing(
        ticketKey: String,
        basePrompt: String,
        briefingContext: BriefingContext?,
    ): String {
        if (briefingContext == null) {
            log.warn("[$ticketKey] no ticket content — briefing skipped, dispatching on fallback prompt")
            return basePrompt
        }
        val briefing = briefingService?.brief(briefingContext)
        return if (briefing != null) {
            log.info("[$ticketKey] briefing generated (${briefing.length} chars) — appending to prompt")
            "$basePrompt\n\n## Agent Briefing\n$briefing"
        } else {
            if (briefingService != null) {
                log.warn("[$ticketKey] briefing returned null — dispatching without briefing")
            }
            basePrompt
        }
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
