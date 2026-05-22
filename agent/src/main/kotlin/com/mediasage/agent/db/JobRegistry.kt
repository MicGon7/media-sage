package com.mediasage.agent.db

import java.util.UUID

interface JobRegistry {
    suspend fun shouldDispatch(ticketKey: String): Boolean
    suspend fun findLatestJob(ticketKey: String): JobRow?
    suspend fun insert(ticketKey: String, prompt: String): UUID
    suspend fun markRunning(jobId: UUID, executionName: String)
    suspend fun markCompleted(jobId: UUID, metrics: WorkerMetrics? = null)
    suspend fun markFailed(jobId: UUID)
    suspend fun markInterrupted(jobId: UUID)
    suspend fun findRunningJobs(): List<JobRow>

    /** Returns the most recent RUNNING job for [ticketKey], or null if none exists. */
    suspend fun findRunningByTicketKey(ticketKey: String): JobRow?
}
