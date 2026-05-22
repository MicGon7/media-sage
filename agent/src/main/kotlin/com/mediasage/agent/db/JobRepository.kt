package com.mediasage.agent.db

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

enum class JobStatus { PENDING, RUNNING, COMPLETED, FAILED, INTERRUPTED }

data class JobRow(
    val jobId: UUID,
    val ticketKey: String,
    val status: JobStatus,
    val executionName: String?
)

data class JobDurationRow(
    val jobId: UUID,
    val ticketKey: String,
    val status: JobStatus,
    val durationSeconds: Int?,
    val startedAt: Instant?,
    val completedAt: Instant?
)

class JobRepository : JobRegistry {

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

    override suspend fun insert(ticketKey: String, prompt: String): UUID = withContext(Dispatchers.IO) {
        val id = UUID.randomUUID()
        transaction {
            JobsTable.insert {
                it[jobId] = id
                it[JobsTable.ticketKey] = ticketKey
                it[JobsTable.prompt] = prompt
                it[status] = JobStatus.PENDING.name
                it[createdAt] = Instant.now()
            }
        }
        id
    }

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

    override suspend fun markCompleted(jobId: UUID, metrics: WorkerMetrics?) = withContext(Dispatchers.IO) {
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
                }
            }
        }
        Unit
    }

    override suspend fun markFailed(jobId: UUID) = withContext(Dispatchers.IO) {
        transaction {
            JobsTable.update({ JobsTable.jobId eq jobId }) {
                it[JobsTable.status] = JobStatus.FAILED.name
                it[JobsTable.completedAt] = Instant.now()
            }
        }
        Unit
    }

    override suspend fun markInterrupted(jobId: UUID) = withContext(Dispatchers.IO) {
        transaction {
            JobsTable.update({ JobsTable.jobId eq jobId }) {
                it[JobsTable.status] = JobStatus.INTERRUPTED.name
                it[JobsTable.completedAt] = Instant.now()
            }
        }
        Unit
    }

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
                        executionName = it[JobsTable.executionName]
                    )
                }
                .firstOrNull()
        }
    }

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

    suspend fun getJobDurations(): List<JobDurationRow> = withContext(Dispatchers.IO) {
        transaction {
            exec(
                "SELECT job_id, ticket_key, status, duration_seconds, started_at, completed_at FROM job_durations ORDER BY started_at DESC"
            ) { rs ->
                val results = mutableListOf<JobDurationRow>()
                while (rs.next()) {
                    val durationSeconds = rs.getInt("duration_seconds").takeIf { !rs.wasNull() }
                    results.add(
                        JobDurationRow(
                            jobId = rs.getObject("job_id", UUID::class.java),
                            ticketKey = rs.getString("ticket_key"),
                            status = JobStatus.valueOf(rs.getString("status")),
                            durationSeconds = durationSeconds,
                            startedAt = rs.getTimestamp("started_at")?.toInstant(),
                            completedAt = rs.getTimestamp("completed_at")?.toInstant()
                        )
                    )
                }
                results
            } ?: emptyList()
        }
    }
}
