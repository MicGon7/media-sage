package com.mediasage.pipeline.core

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Pub/Sub message payload published by a worker when a Cloud Run job execution completes.
 *
 * The worker publishes this to the `cloud-run-job-completions` topic; the orchestrator
 * receives it via HTTP push and uses it to look up and finalize the corresponding job row.
 */
@Serializable
data class JobCompletionEvent(
    @SerialName("ticketKey")
    val ticketKey: String,
    @SerialName("executionName")
    val executionName: String,
    @SerialName("status")
    val status: String, // "success" or "failure"
    /**
     * The job family that produced this completion (e.g. `ticket-work`, `pr-quality-work`,
     * `pr-review-work`, `conflict-resolution-work`). Published verbatim from the worker's
     * `JOB_TYPE` env var so the notifier can name the job without string-parsing the dedup key.
     * Null when published by an older worker or the recovery path.
     */
    @SerialName("jobType")
    val jobType: String? = null,
    /**
     * The actual Jira issue key (e.g. "MS-257") when [ticketKey] is a synthetic dedup key
     * (e.g. "PR-200", "CONFLICT-199"). Set only for PR review and conflict resolution jobs.
     * When present, used in place of [ticketKey] for Jira comment posting.
     */
    @SerialName("jiraTicketKey")
    val jiraTicketKey: String? = null,
    /**
     * GitHub PR number opened by the worker. Injected into the judge prompt so the judge
     * can skip the `gh pr list` discovery turn. Null if the worker did not publish it.
     */
    @SerialName("prNumber")
    val prNumber: Int? = null,
    /**
     * Number of review comments a review-type job (`pr-quality-work`, `pr-review-work`) posted to
     * the PR. `0` renders as `clean`; a positive count as `N comments`. Null for non-review jobs and
     * for older workers that did not report it — the notifier omits the signal in that case.
     */
    @SerialName("reviewCommentCount")
    val reviewCommentCount: Int? = null,
    /**
     * Worker efficiency metrics parsed from the Claude Code `result` event and embedded in
     * the payload by the worker. All null when the event was published by an older worker that
     * did not include metrics.
     */
    @SerialName("numTurns")
    val numTurns: Int? = null,
    @SerialName("totalCostUsd")
    val totalCostUsd: Double? = null,
    @SerialName("durationMs")
    val durationMs: Long? = null,
    @SerialName("modelVersion")
    val modelVersion: String? = null,
    /**
     * Reasoning effort the worker ran under (e.g. `high`, `low`), published verbatim from the
     * worker's `WORKER_EFFORT` env var. Recorded alongside [modelVersion] as the run's execution
     * config. Null when published by an older worker.
     */
    @SerialName("effort")
    val effort: String? = null,
    @SerialName("inputTokens")
    val inputTokens: Int? = null,
    @SerialName("outputTokens")
    val outputTokens: Int? = null,
    @SerialName("cacheReadTokens")
    val cacheReadTokens: Int? = null,
    @SerialName("cacheCreationTokens")
    val cacheCreationTokens: Int? = null,
)
