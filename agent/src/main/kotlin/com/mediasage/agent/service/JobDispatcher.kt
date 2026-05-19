package com.mediasage.agent.service

import java.util.UUID

interface JobDispatcher {
    suspend fun executeJob(jobId: UUID, ticketKey: String, prompt: String): Boolean

    /**
     * Called on orchestrator startup for each RUNNING job whose LRO poll was lost.
     * Default is a no-op; Cloud Run implementation resumes polling the saved execution.
     */
    suspend fun recoverJob(jobId: UUID, ticketKey: String, executionName: String) {}
}
