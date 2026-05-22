package com.mediasage.agent.db

import java.util.UUID

/**
 * Persistent job registry backed by Supabase Postgres. Tracks the lifecycle of Cloud Run worker
 * jobs to prevent duplicate dispatch and support crash recovery on agent restart.
 *
 * Job state machine: `PENDING → RUNNING → COMPLETED | FAILED | INTERRUPTED`
 */
interface JobRegistry {
    /**
     * Returns true if a new worker job should be dispatched for [ticketKey].
     *
     * Returns false if the latest job for the ticket is RUNNING (concurrent duplicate) or COMPLETED
     * (permanent dedup). Returns true if it is FAILED or INTERRUPTED (retry eligible) or if no job
     * exists yet.
     */
    suspend fun shouldDispatch(ticketKey: String): Boolean

    /**
     * Returns the most recently created [JobRow] for [ticketKey], or null if no job exists.
     */
    suspend fun findLatestJob(ticketKey: String): JobRow?

    /**
     * Inserts a new job row in PENDING status for the given [ticketKey] and [prompt].
     *
     * @return The generated UUID for the new job.
     */
    suspend fun insert(ticketKey: String, prompt: String): UUID

    /**
     * Transitions [jobId] to RUNNING, recording the Cloud Run [executionName] and start timestamp.
     */
    suspend fun markRunning(jobId: UUID, executionName: String)

    /**
     * Transitions [jobId] to COMPLETED, recording the completion timestamp and optionally
     * persisting worker efficiency [metrics] captured from the Claude Code result event.
     */
    suspend fun markCompleted(jobId: UUID, metrics: WorkerMetrics? = null)

    /**
     * Transitions [jobId] to FAILED and records the completion timestamp.
     */
    suspend fun markFailed(jobId: UUID)

    /**
     * Transitions [jobId] to INTERRUPTED and records the completion timestamp. Used during crash
     * recovery when the Cloud Run execution is no longer found for a RUNNING job.
     */
    suspend fun markInterrupted(jobId: UUID)

    /**
     * Returns all jobs currently in RUNNING status. Called on agent startup to recover any jobs
     * that were left running when the agent last shut down.
     */
    suspend fun findRunningJobs(): List<JobRow>
}
