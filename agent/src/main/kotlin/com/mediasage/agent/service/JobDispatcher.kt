package com.mediasage.agent.service

import java.util.UUID

/**
 * Executes and recovers agent jobs on behalf of the orchestrator.
 *
 * Implementations choose the execution backend — in-process for local development or
 * Cloud Run Jobs for production. [AgentLaunchService] depends on this interface so the
 * backend can be swapped via Koin without changing dispatch logic.
 */
interface JobDispatcher {
    suspend fun executeJob(jobId: UUID, ticketKey: String, prompt: String): Boolean

    /**
     * Called on orchestrator startup for each RUNNING job whose LRO poll was lost.
     * Returns false if the execution was gone and the job was marked INTERRUPTED.
     * Default is a no-op that returns true; Cloud Run implementation overrides.
     */
    suspend fun recoverJob(jobId: UUID, ticketKey: String, executionName: String): Boolean = true
}
