package com.mediasage.agentruntime.service

import com.mediasage.pipeline.core.JobCompletionEvent
import org.slf4j.LoggerFactory

private const val SUCCESS_STATUS = "success"

/** Job types that post findings to a PR and therefore carry a programmatic review signal. */
private val REVIEW_JOB_TYPES = setOf("pr-quality-work", "pr-review-work")

/**
 * Builds a facts-only Slack message for each job completion and posts it via [SlackApiClient].
 *
 * Coordinates the transport ([SlackApiClient]) — no LLM is involved. On every completion it posts
 * the run's performance facts (turns, cost, duration, PR link, and a review signal for review jobs).
 *
 * All work is wrapped so a Slack failure only logs — it never disrupts the completion pipeline.
 *
 * @param slackClient Transport for the outgoing webhook message.
 * @param repoOwner GitHub repository owner, used to build the PR link. Blank omits the link.
 * @param repoName GitHub repository name, used to build the PR link. Blank omits the link.
 */
class JobCompletionNotifier(
    private val slackClient: SlackApiClient,
    private val repoOwner: String,
    private val repoName: String,
) {

    private val log = LoggerFactory.getLogger(JobCompletionNotifier::class.java)

    /** Posts the completion facts for [event] to Slack. */
    suspend fun notifyCompletion(event: JobCompletionEvent) {
        try {
            slackClient.send(buildCompletionMessage(event, prUrl(event.prNumber)))
        } catch (e: Exception) {
            log.warn("[${event.jiraTicketKey ?: event.ticketKey}] Failed to notify completion: ${e.message}")
        }
    }

    private fun prUrl(prNumber: Int?): String? =
        if (prNumber != null && repoOwner.isNotBlank() && repoName.isNotBlank()) {
            "https://github.com/$repoOwner/$repoName/pull/$prNumber"
        } else {
            null
        }
}

/**
 * Renders the facts-only completion message: status header (with the job type when known) followed
 * by turns, cost, duration, a review signal for review-type jobs, and the PR link. Missing metrics
 * (old worker or recovery path) render as `n/a`; a missing job type or review signal is simply omitted.
 */
internal fun buildCompletionMessage(
    event: JobCompletionEvent,
    prUrl: String?,
): String {
    val key = event.jiraTicketKey ?: event.ticketKey
    val icon = if (event.status == SUCCESS_STATUS) "✅" else "❌"
    val header = event.jobType?.let { "$icon *$key* — $it — ${event.status}" }
        ?: "$icon *$key* — ${event.status}"
    val lines = mutableListOf(header)
    lines += "• turns: ${event.numTurns?.toString() ?: "n/a"}"
    lines += "• cost: ${event.totalCostUsd?.let(::formatCost) ?: "n/a"}"
    lines += "• duration: ${event.durationMs?.let(::formatDuration) ?: "n/a"}"
    reviewSignal(event)?.let { lines += "• review: $it" }
    prUrl?.let { lines += "• PR: $it" }
    return lines.joinToString("\n")
}

/**
 * Programmatic review signal for review-type jobs: `clean` when zero comments were posted, otherwise
 * `N comments`. Null (line omitted) for non-review jobs or when the worker did not report a count.
 */
private fun reviewSignal(event: JobCompletionEvent): String? {
    if (event.jobType !in REVIEW_JOB_TYPES) return null
    val count = event.reviewCommentCount ?: return null
    return if (count == 0) "clean" else "$count comment${if (count == 1) "" else "s"}"
}

private fun formatCost(usd: Double): String = "$" + String.format(java.util.Locale.US, "%.4f", usd)

private fun formatDuration(ms: Long): String {
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return if (minutes > 0) "${minutes}m ${seconds}s" else "${seconds}s"
}
