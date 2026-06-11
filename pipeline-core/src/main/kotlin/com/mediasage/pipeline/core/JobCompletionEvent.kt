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
)
