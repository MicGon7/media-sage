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
     * Dispatches a job to the execution backend.
     *
     * @param ticketKey Dedup key used for logging (e.g. "MS-123", "PR-456", "CONFLICT-456").
     * @param jobType Skill name the worker entrypoint will invoke (e.g. "ticket-work").
     * @param identifiers Minimum job identifiers passed as env vars to the worker
     *   (e.g. `{"TICKET_KEY" to "MS-123"}`). The worker fetches all other context at runtime.
     * @param jobNameOverride Overrides the default Cloud Run job name (used for the judge job).
     */
    suspend fun executeJob(
        jobId: UUID,
        ticketKey: String,
        jobType: String,
        identifiers: Map<String, String>,
        jobNameOverride: String? = null,
    ): Boolean

    /**
     * Called on orchestrator startup for each RUNNING job whose LRO poll was lost.
     * Returns false if the execution was gone and the job was marked INTERRUPTED.
     * Default is a no-op that returns true; Cloud Run implementation overrides.
     */
    suspend fun recoverJob(jobId: UUID, ticketKey: String, executionName: String): Boolean = true
}
