package com.mediasage.agentruntime.service

import com.mediasage.pipeline.core.JobStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.concurrent.ConcurrentHashMap
import org.slf4j.Logger
import org.slf4j.LoggerFactory

private data class DispatchOptions(
    val dryRun: Boolean = false,
    val jobNameOverride: String? = null,
    val blockerKey: String? = null,
)

/**
 * Dispatches autonomous Claude Code agents via Cloud Run Jobs.
 * Guards against double-firing: a second launch call for the same key is a no-op
 * until the first dispatch coroutine completes.
 *
 * The orchestrator is a pure dispatcher: each launch method passes only the minimum
 * job identifiers as env vars. The worker and its skills own all framing and context
 * fetching. No prompt strings are constructed here.
 */
class AgentLaunchService(
    private val scope: CoroutineScope,
    internal val cloudRun: CloudRunDispatch? = null,
    private val jiraApiClient: JiraApiClient? = null,
) : AgentLauncher {

    private val log = LoggerFactory.getLogger(AgentLaunchService::class.java)

    // Atomic dedup gate — Set.add() returns false if key already present, in one operation.
    // This prevents the TOCTOU race condition that ConcurrentHashMap.containsKey() + put() would have.
    private val activeKeys: MutableSet<String> = ConcurrentHashMap.newKeySet()

    private val json = Json { encodeDefaults = false }

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
     * @param ticketKey Jira issue key (e.g. "MS-123"). Used as the dedup key and forwarded as
     *   `TICKET_KEY`. The worker fetches ticket content from Jira at runtime.
     * @param dryRun If true, inserts a job row but skips execution dispatch.
     * @return true if an agent was dispatched; false if the call was deduplicated or Cloud Run is not configured.
     */
    override fun launch(ticketKey: String, dryRun: Boolean): Boolean {
        val cloudRun = cloudRun ?: return false
        val payload = json.encodeToString(mapOf("ticketKey" to ticketKey))
        val identifiers = mapOf("TICKET_KEY" to ticketKey)
        return dispatchToCloudRun(ticketKey, "ticket-work", payload, identifiers, cloudRun, DispatchOptions(dryRun = dryRun))
    }

    private fun dispatchToCloudRun(
        ticketKey: String,
        jobType: String,
        payload: String,
        identifiers: Map<String, String>,
        cloudRun: CloudRunDispatch,
        options: DispatchOptions = DispatchOptions(),
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
                doDispatch(ticketKey, jobType, payload, identifiers, cloudRun, options)
            } finally {
                activeKeys.remove(ticketKey)
            }
        }
        return true
    }

    private suspend fun doDispatch(
        ticketKey: String,
        jobType: String,
        payload: String,
        identifiers: Map<String, String>,
        cloudRun: CloudRunDispatch,
        options: DispatchOptions,
    ) {
        if (!cloudRun.jobs.shouldDispatch(ticketKey)) {
            log.info("[$ticketKey] job already running or completed — ignoring duplicate webhook")
            return
        }
        if (shouldSkipInterrupted(ticketKey, cloudRun, jiraApiClient, log)) return
        options.blockerKey?.let { blocker ->
            jiraApiClient?.addComment(ticketKey, "🤖 Dispatched automatically after **$blocker** was merged.")
        }
        val jobId = cloudRun.jobs.insert(ticketKey, payload)
        if (options.dryRun) {
            log.info("[$ticketKey] dry-run: job $jobId inserted — skipping Cloud Run dispatch")
            cloudRun.jobs.markFailed(jobId)
            return
        }
        log.info("[$ticketKey] job $jobId inserted — dispatching to Cloud Run")
        try {
            cloudRun.dispatcher.executeJob(jobId, ticketKey, jobType, identifiers, options.jobNameOverride)
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
                postInterruptedComment(job.ticketKey, jiraApiClient)
                return@forEach
            }
            scope.launch {
                val recovered = cloudRun.dispatcher.recoverJob(job.jobId, job.ticketKey, executionName)
                if (!recovered) postInterruptedComment(job.ticketKey, jiraApiClient)
            }
        }
    }

    /**
     * Launches a Cloud Run Job to respond to a PR review comment.
     *
     * Deduplicates by [prNumber] (`PR-{prNumber}`). The worker derives branch ref, reviewer
     * login, and ticket key from `gh pr view $PR_NUMBER` at runtime.
     *
     * @param prNumber GitHub PR number. Used as the dedup key and passed as `PR_NUMBER`.
     * @return true if dispatched; false if deduplicated or Cloud Run is not configured.
     */
    override fun launchForPrReview(prNumber: Int): Boolean {
        val cloudRun = cloudRun ?: return false
        val key = "PR-$prNumber"
        val payload = json.encodeToString(mapOf("prNumber" to prNumber.toString()))
        val identifiers = mapOf("PR_NUMBER" to prNumber.toString())
        return dispatchToCloudRun(key, "pr-review-work", payload, identifiers, cloudRun)
    }

    /**
     * Launches a Cloud Run Job to rebase a branch ejected from the merge queue due to a conflict.
     *
     * Deduplicates by [prNumber] using the key `CONFLICT-{prNumber}`. The worker derives
     * branch ref and base branch from `gh pr view $PR_NUMBER` at runtime.
     *
     * @param prNumber GitHub PR number. Used as the dedup key and passed as `PR_NUMBER`.
     * @return true if dispatched; false if deduplicated or Cloud Run is not configured.
     */
    override fun launchForConflictResolution(prNumber: Int): Boolean {
        val cloudRun = cloudRun ?: return false
        val key = "CONFLICT-$prNumber"
        val payload = json.encodeToString(mapOf("prNumber" to prNumber.toString()))
        val identifiers = mapOf("PR_NUMBER" to prNumber.toString())
        return dispatchToCloudRun(key, "conflict-resolution-work", payload, identifiers, cloudRun)
    }

    /**
     * Launches a Cloud Run Job for [ticketKey] that was unblocked when [blockerKey]'s PR merged.
     *
     * Posts a Jira comment on [ticketKey] citing [blockerKey] as the trigger, then dispatches.
     * The comment is posted inside the dedup-guarded coroutine so it only fires on actual dispatch.
     *
     * @param ticketKey Newly unblocked ticket key (e.g. "MS-521").
     * @param blockerKey Blocker ticket key whose PR just merged (e.g. "MS-520").
     * @return true if dispatched; false if deduplicated or Cloud Run is not configured.
     */
    override fun launchForUnblockedTicket(ticketKey: String, blockerKey: String): Boolean {
        val cloudRun = cloudRun ?: return false
        val payload = json.encodeToString(mapOf("ticketKey" to ticketKey))
        val identifiers = mapOf("TICKET_KEY" to ticketKey)
        return dispatchToCloudRun(
            ticketKey, "ticket-work", payload, identifiers, cloudRun,
            DispatchOptions(blockerKey = blockerKey),
        )
    }

    /**
     * Returns true if [key] currently has a dispatch coroutine in flight.
     * Used in tests to assert that a dispatch was started without waiting for it to complete.
     *
     * @param key Dedup key — a Jira ticket key (e.g. "MS-123") for ticket launches,
     *   or a derived key like "PR-456" or "CONFLICT-456" for PR-driven launches.
     */
    fun isActive(key: String): Boolean = key in activeKeys

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
    checker: JiraApiClient?,
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

private suspend fun postInterruptedComment(ticketKey: String, poster: JiraApiClient?) {
    poster?.addComment(
        ticketKey,
        "⚠️ The agent job for this ticket was interrupted during an orchestrator restart. " +
            "To re-trigger, move the ticket back to **In Progress**."
    )
}
