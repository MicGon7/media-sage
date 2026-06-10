package com.mediasage.agent.db

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

    /**
     * Atomically checks whether a job should be dispatched and, if so, inserts a PENDING row.
     *
     * Uses a Postgres advisory lock keyed on [ticketKey] so that concurrent calls from
     * multiple orchestrator instances are serialized — only one instance can check-and-insert
     * at a time. Returns the new job [UUID] if dispatch is allowed, or null if the latest
     * job is PENDING, RUNNING, or COMPLETED (dedup).
     *
     * Prefer this over separate [shouldDispatch] + [insert] calls to avoid the TOCTOU race
     * where two instances both see "no row" before either inserts.
     */
    suspend fun tryInsertIfDispatchable(ticketKey: String, prompt: String): UUID?

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
