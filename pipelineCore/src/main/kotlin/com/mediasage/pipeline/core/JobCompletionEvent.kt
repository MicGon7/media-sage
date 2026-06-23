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
     * Quality gate the worker reported as the cause of a failed run (e.g. `compile`, `tests`,
     * `detekt`, `ci`). Set only when [status] is `failure` and the worker wrote a gate name;
     * null otherwise. Persisted to `jobs.failed_gate` for failure attribution (MS-386).
     */
    @SerialName("failedGate")
    val failedGate: String? = null,
    /**
     * Worker efficiency metrics parsed from the Claude Code `result` event and embedded in
     * the payload by the worker (MS-412). All null when the event was published by an older
     * worker that did not include metrics.
     */
    @SerialName("numTurns")
    val numTurns: Int? = null,
    @SerialName("totalCostUsd")
    val totalCostUsd: Double? = null,
    @SerialName("durationMs")
    val durationMs: Long? = null,
    @SerialName("modelVersion")
    val modelVersion: String? = null,
    @SerialName("inputTokens")
    val inputTokens: Int? = null,
    @SerialName("outputTokens")
    val outputTokens: Int? = null,
    @SerialName("cacheReadTokens")
    val cacheReadTokens: Int? = null,
    @SerialName("cacheCreationTokens")
    val cacheCreationTokens: Int? = null,
    /**
     * Unix epoch milliseconds captured at container start in `entrypoint-common.sh` (MS-414).
     * Used by the orchestrator to compute `env_startup_ms` = this value minus `jobs.started_at`.
     * Null when published by an older worker that did not include this field.
     */
    @SerialName("containerStartedAtMs")
    val containerStartedAtMs: Long? = null,
)
