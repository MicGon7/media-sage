package com.mediasage.pipeline.core

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

/**
 * Lifecycle states for an autonomous agent job.
 *
 * State machine: `PENDING → RUNNING → COMPLETED | FAILED | INTERRUPTED`
 *
 * - [PENDING] — row inserted but Cloud Run dispatch not yet confirmed
 * - [RUNNING] — Cloud Run execution is active; dedup gate blocks concurrent dispatches for the same ticket
 * - [COMPLETED] — worker finished successfully; permanently deduplicated (no re-dispatch)
 * - [FAILED] — worker exited with an error; eligible for retry on the next webhook event
 * - [INTERRUPTED] — orchestrator restarted while the job was RUNNING and the Cloud Run execution
 *   was no longer found; eligible for manual re-trigger
 */
enum class JobStatus { PENDING, RUNNING, COMPLETED, FAILED, INTERRUPTED }

/**
 * Lightweight projection of a `jobs` row used by dispatch and recovery logic.
 *
 * Only the fields needed for dedup decisions and LRO recovery are included — heavy fields
 * like [JobsTable.prompt] and metric columns are omitted to keep query payloads small.
 */
data class JobRow(
    val jobId: UUID,
    val ticketKey: String,
    val status: JobStatus,
    val executionName: String?,
    /** Timestamp when the Cloud Run execution started (set in [JobRepository.markRunning]). */
    val startedAt: Instant? = null
)

/**
 * Projection of the `job_durations` Postgres view, which pre-computes elapsed time as an
 * integer number of seconds between [startedAt] and [completedAt].
 *
 * [durationSeconds] is null for jobs still in the RUNNING state (no [completedAt] yet).
 */
data class JobDurationRow(
    val jobId: UUID,
    val ticketKey: String,
    val status: JobStatus,
    val durationSeconds: Int?,
    val startedAt: Instant?,
    val completedAt: Instant?,
    /**
     * Environment startup time in milliseconds (MS-399): dispatch → the worker container's first
     * log line (Cloud Run cold start + image pull). Null when not recorded for this job.
     */
    val envStartupMs: Long?
)

/**
 * Supabase Postgres implementation of [JobRegistry].
 *
 * All queries run on [Dispatchers.IO] via [withContext] and use synchronous Exposed
 * transactions. This is correct because Exposed's transaction DSL is blocking and must
 * not run on the default coroutine dispatcher.
 */
class JobRepository : JobRegistry {

    /**
     * Returns true if a job for [ticketKey] should be dispatched.
     *
     * Skips dispatch when the latest row for [ticketKey] is RUNNING (concurrent duplicate)
     * or COMPLETED (permanent dedup). Returns true for FAILED or INTERRUPTED rows so those
     * tickets can be retried, and also when no row exists yet.
     */
    override suspend fun shouldDispatch(ticketKey: String): Boolean = withContext(Dispatchers.IO) {
        val latest = transaction {
            JobsTable.selectAll()
                .where { JobsTable.ticketKey eq ticketKey }
                .orderBy(JobsTable.createdAt, SortOrder.DESC)
                .limit(1)
                .map { it[JobsTable.status] }
                .firstOrNull()
        }
        // Skip if already running or completed; retry if failed or interrupted
        latest != JobStatus.RUNNING.name && latest != JobStatus.COMPLETED.name
    }

    /** Returns the most recently created [JobRow] for [ticketKey], or null if no row exists. */
    override suspend fun findLatestJob(ticketKey: String): JobRow? = withContext(Dispatchers.IO) {
        transaction {
            JobsTable.selectAll()
                .where { JobsTable.ticketKey eq ticketKey }
                .orderBy(JobsTable.createdAt, SortOrder.DESC)
                .limit(1)
                .map {
                    JobRow(
                        jobId = it[JobsTable.jobId],
                        ticketKey = ticketKey,
                        status = JobStatus.valueOf(it[JobsTable.status]),
                        executionName = it[JobsTable.executionName]
                    )
                }
                .firstOrNull()
        }
    }

    /**
     * Inserts a new PENDING job row for [ticketKey] with the given [payload] (compact JSON of
     * dispatched identifiers) and returns the generated [UUID] that identifies the job.
     */
    override suspend fun insert(ticketKey: String, payload: String): UUID = withContext(Dispatchers.IO) {
        val id = UUID.randomUUID()
        transaction {
            JobsTable.insert {
                it[jobId] = id
                it[JobsTable.ticketKey] = ticketKey
                it[JobsTable.payload] = payload
                it[status] = JobStatus.PENDING.name
                it[createdAt] = Instant.now()
            }
        }
        id
    }

    /**
     * Transitions [jobId] to RUNNING, records the Cloud Run [executionName], and stamps
     * [JobsTable.startedAt] with the current time.
     */
    override suspend fun markRunning(jobId: UUID, executionName: String) = withContext(Dispatchers.IO) {
        transaction {
            JobsTable.update({ JobsTable.jobId eq jobId }) {
                it[JobsTable.status] = JobStatus.RUNNING.name
                it[JobsTable.executionName] = executionName
                it[JobsTable.startedAt] = Instant.now()
            }
        }
        Unit
    }

    /**
     * Transitions [jobId] to COMPLETED and stamps [JobsTable.completedAt].
     *
     * If [metrics] is non-null, also persists token counts, cost, Claude duration, turn
     * count, and model version sourced from the Cloud Logging result event. These columns
     * remain null when metrics are unavailable (e.g. Cloud Logging ingestion timeout).
     */
    override suspend fun markCompleted(jobId: UUID, metrics: WorkerMetrics?, envStartupMs: Long?) =
        withContext(Dispatchers.IO) {
            transaction {
                JobsTable.update({ JobsTable.jobId eq jobId }) {
                    it[JobsTable.status] = JobStatus.COMPLETED.name
                    it[JobsTable.completedAt] = Instant.now()
                    if (metrics != null) {
                        it[JobsTable.inputTokens] = metrics.inputTokens
                        it[JobsTable.outputTokens] = metrics.outputTokens
                        it[JobsTable.cacheReadTokens] = metrics.cacheReadTokens
                        it[JobsTable.cacheCreationTokens] = metrics.cacheCreationTokens
                        it[JobsTable.totalCostUsd] = BigDecimal.valueOf(metrics.totalCostUsd)
                        it[JobsTable.claudeDurationMs] = metrics.durationMs
                        it[JobsTable.numTurns] = metrics.numTurns
                        it[JobsTable.modelVersion] = metrics.modelVersion
                    }
                    if (envStartupMs != null) it[JobsTable.envStartupMs] = envStartupMs
                }
            }
            Unit
        }

    /**
     * Transitions [jobId] to FAILED and stamps [JobsTable.completedAt].
     *
     * Records [failedGate] (the quality gate the worker reported as the failure cause) and
     * [modelVersion] (best-effort from the result event) when provided; both stay null on
     * paths with no such info, e.g. LRO/dispatch failures (MS-386).
     */
    override suspend fun markFailed(jobId: UUID, failedGate: String?, modelVersion: String?) = withContext(Dispatchers.IO) {
        transaction {
            JobsTable.update({ JobsTable.jobId eq jobId }) {
                it[JobsTable.status] = JobStatus.FAILED.name
                if (failedGate != null) it[JobsTable.failedGate] = failedGate
                if (modelVersion != null) it[JobsTable.modelVersion] = modelVersion
                it[JobsTable.completedAt] = Instant.now()
            }
        }
        Unit
    }

    /**
     * Transitions [jobId] to INTERRUPTED and stamps [JobsTable.completedAt].
     *
     * Called by startup recovery when a RUNNING execution is no longer present in Cloud Run
     * and cannot be resumed via LRO polling.
     */
    override suspend fun markInterrupted(jobId: UUID) = withContext(Dispatchers.IO) {
        transaction {
            JobsTable.update({ JobsTable.jobId eq jobId }) {
                it[JobsTable.status] = JobStatus.INTERRUPTED.name
                it[JobsTable.completedAt] = Instant.now()
            }
        }
        Unit
    }

    /** Returns the most recent RUNNING job for [ticketKey], or null if none is active. */
    override suspend fun findRunningByTicketKey(ticketKey: String): JobRow? = withContext(Dispatchers.IO) {
        transaction {
            JobsTable.selectAll()
                .where { (JobsTable.ticketKey eq ticketKey) and (JobsTable.status eq JobStatus.RUNNING.name) }
                .orderBy(JobsTable.createdAt, SortOrder.DESC)
                .limit(1)
                .map {
                    JobRow(
                        jobId = it[JobsTable.jobId],
                        ticketKey = ticketKey,
                        status = JobStatus.RUNNING,
                        executionName = it[JobsTable.executionName],
                        startedAt = it[JobsTable.startedAt]
                    )
                }
                .firstOrNull()
        }
    }

    /**
     * Returns all jobs currently in the RUNNING state, across all ticket keys.
     *
     * Used by startup recovery to find executions whose LRO poll was lost on orchestrator
     * restart, so they can be resumed or marked INTERRUPTED.
     */
    override suspend fun findRunningJobs(): List<JobRow> = withContext(Dispatchers.IO) {
        transaction {
            JobsTable.selectAll()
                .where { JobsTable.status eq JobStatus.RUNNING.name }
                .map {
                    JobRow(
                        jobId = it[JobsTable.jobId],
                        ticketKey = it[JobsTable.ticketKey],
                        status = JobStatus.RUNNING,
                        executionName = it[JobsTable.executionName]
                    )
                }
        }
    }

    /**
     * Returns all rows from the `job_durations` Postgres view, ordered by [JobDurationRow.startedAt]
     * descending.
     *
     * The view pre-computes elapsed time between [JobDurationRow.startedAt] and
     * [JobDurationRow.completedAt] as an integer number of seconds. A null [JobDurationRow.durationSeconds]
     * indicates the job has not yet left the RUNNING state.
     */
    suspend fun getJobDurations(): List<JobDurationRow> = withContext(Dispatchers.IO) {
        transaction {
            exec(
                "SELECT job_id, ticket_key, status, duration_seconds, started_at, completed_at, env_startup_ms " +
                    "FROM job_durations ORDER BY started_at DESC"
            ) { rs ->
                val results = mutableListOf<JobDurationRow>()
                while (rs.next()) {
                    val durationSeconds = rs.getInt("duration_seconds").takeIf { !rs.wasNull() }
                    val envStartupMs = rs.getLong("env_startup_ms").takeIf { !rs.wasNull() }
                    results.add(
                        JobDurationRow(
                            jobId = rs.getObject("job_id", UUID::class.java),
                            ticketKey = rs.getString("ticket_key"),
                            status = JobStatus.valueOf(rs.getString("status")),
                            durationSeconds = durationSeconds,
                            startedAt = rs.getTimestamp("started_at")?.toInstant(),
                            completedAt = rs.getTimestamp("completed_at")?.toInstant(),
                            envStartupMs = envStartupMs
                        )
                    )
                }
                results
            } ?: emptyList()
        }
    }
}
