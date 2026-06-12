package com.mediasage.orchestrator.service

import java.util.UUID

/**
 * Executes and recovers agent jobs on behalf of the orchestrator.
 *
 * Implementations choose the execution backend — in-process for local development or
 * Cloud Run Jobs for production. [AgentLaunchService] depends on this interface so the
 * backend can be swapped via Koin without changing dispatch logic.
 */
interface JobDispatcher {
    /**
     * @param jiraTicketKey The actual Jira issue key (e.g. "MS-257") when it differs from [ticketKey].
     *   For standard autonomous launches [ticketKey] IS the Jira key, so this is null.
     *   For PR review and conflict resolution, [ticketKey] is a synthetic dedup key ("PR-200",
     *   "CONFLICT-199") and [jiraTicketKey] carries the real Jira key for comment posting.
     */
    suspend fun executeJob(
        jobId: UUID,
        ticketKey: String,
        prompt: String,
        jiraTicketKey: String? = null,
        jobNameOverride: String? = null,
    ): Boolean

    /**
     * Called on orchestrator startup for each RUNNING job whose LRO poll was lost.
     * Returns false if the execution was gone and the job was marked INTERRUPTED.
     * Default is a no-op that returns true; Cloud Run implementation overrides.
     */
    suspend fun recoverJob(jobId: UUID, ticketKey: String, executionName: String): Boolean = true
}
