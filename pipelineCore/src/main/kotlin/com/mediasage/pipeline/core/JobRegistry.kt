package com.mediasage.pipeline.core

import java.util.UUID

/**
 * Persistent store for agent job state, backed by Supabase Postgres.
 *
 * Replaces the in-memory dedup gate so job state survives orchestrator restarts. Before
 * dispatching a Cloud Run worker, callers check [shouldDispatch] to enforce the dedup policy:
 * skip if a job is already RUNNING or COMPLETED for the same ticket, retry if FAILED or
 * INTERRUPTED. On startup, [findRunningJobs] is used by recovery logic to resume or mark
 * interrupted executions.
 */
interface JobRegistry {
    suspend fun shouldDispatch(ticketKey: String): Boolean
    suspend fun findLatestJob(ticketKey: String): JobRow?
    suspend fun insert(ticketKey: String, payload: String): UUID
    suspend fun markRunning(jobId: UUID, executionName: String)

    /** Marks [jobId] COMPLETED. Optionally records worker [metrics] (from the result event). */
    suspend fun markCompleted(jobId: UUID, metrics: WorkerMetrics? = null)

    /**
     * Marks [jobId] FAILED, optionally recording the [failedGate] that caused the failure
     * (reported by the worker) and the [modelVersion] that ran (MS-386). Both are nullable —
     * paths with no gate info (LRO/dispatch failures) and runs where the model is unavailable
     * pass null.
     */
    suspend fun markFailed(jobId: UUID, failedGate: String? = null, modelVersion: String? = null)
    suspend fun markInterrupted(jobId: UUID)
    suspend fun findRunningJobs(): List<JobRow>

    /** Returns the most recent RUNNING job for [ticketKey], or null if none exists. */
    suspend fun findRunningByTicketKey(ticketKey: String): JobRow?
}
