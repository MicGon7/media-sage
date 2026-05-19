package com.mediasage.agent.db

import java.util.UUID

interface JobRegistry {
    suspend fun shouldDispatch(ticketKey: String): Boolean
    suspend fun insert(ticketKey: String, prompt: String): UUID
    suspend fun markRunning(jobId: UUID, executionName: String)
    suspend fun markCompleted(jobId: UUID)
    suspend fun markFailed(jobId: UUID)
    suspend fun markInterrupted(jobId: UUID)
    suspend fun findRunningJobs(): List<JobRow>
}
