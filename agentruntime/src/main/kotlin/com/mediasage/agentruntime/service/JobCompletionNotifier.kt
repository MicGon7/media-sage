package com.mediasage.agentruntime.service

import com.mediasage.agentruntime.feedback.detector.DetectedPattern
import com.mediasage.agentruntime.feedback.detector.PatternDetector
import com.mediasage.pipeline.core.JobCompletionEvent
import org.slf4j.LoggerFactory

private const val SUCCESS_STATUS = "success"

/** Job types that post findings to a PR and therefore carry a programmatic review signal. */
private val REVIEW_JOB_TYPES = setOf("pr-quality-work", "pr-review-work")

/**
 * Builds a facts-only Slack message for each job completion and posts it via [SlackApiClient].
 *
 * Coordinates the transport ([SlackApiClient]) and the programmatic gate-failure detector
 * ([PatternDetector]) — no LLM is involved. On every completion it posts the run's performance
 * facts; when a quality gate has failed repeatedly inside the detection window it appends a
 * louder ⚠️ trend line.
 *
 * All work is wrapped so a Slack or database failure only logs — it never disrupts the
 * completion pipeline.
 *
 * @param slackClient Transport for the outgoing webhook message.
 * @param patternDetector Programmatic detector supplying gate-failure trends.
 * @param repoOwner GitHub repository owner, used to build the PR link. Blank omits the link.
 * @param repoName GitHub repository name, used to build the PR link. Blank omits the link.
 */
class JobCompletionNotifier(
    private val slackClient: SlackApiClient,
    private val patternDetector: PatternDetector,
    private val repoOwner: String,
    private val repoName: String,
) {

    private val log = LoggerFactory.getLogger(JobCompletionNotifier::class.java)

    /** Posts the completion facts for [event] to Slack, appending any gate-failure trend flag. */
    suspend fun notifyCompletion(event: JobCompletionEvent) {
        try {
            val gates = runCatching { patternDetector.detectPatterns() }
                .getOrDefault(emptyList())
                .filterIsInstance<DetectedPattern.GateFailure>()
            slackClient.send(buildCompletionMessage(event, gates, prUrl(event.prNumber)))
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
 * by turns, cost, duration, failed gate, a review signal for review-type jobs, and the PR link, then
 * one ⚠️ line per gate-failure trend that crossed threshold. Missing metrics (old worker or recovery
 * path) render as `n/a`; a missing job type or review signal is simply omitted.
 */
internal fun buildCompletionMessage(
    event: JobCompletionEvent,
    gatePatterns: List<DetectedPattern.GateFailure>,
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
    lines += "• gate: ${event.failedGate ?: "none"}"
    reviewSignal(event)?.let { lines += "• review: $it" }
    prUrl?.let { lines += "• PR: $it" }
    gatePatterns.forEach { lines += formatGateTrend(it) }
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

internal fun formatGateTrend(pattern: DetectedPattern.GateFailure): String =
    "⚠️ gate `${pattern.gate}` failed in ${pattern.runCount} runs over the last ${pattern.windowDays} days"

private fun formatCost(usd: Double): String = "$" + String.format(java.util.Locale.US, "%.4f", usd)

private fun formatDuration(ms: Long): String {
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return if (minutes > 0) "${minutes}m ${seconds}s" else "${seconds}s"
}
