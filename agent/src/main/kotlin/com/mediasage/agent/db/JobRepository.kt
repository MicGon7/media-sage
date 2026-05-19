package com.mediasage.agent.db

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import java.time.Instant
import java.util.UUID

enum class JobStatus { PENDING, RUNNING, COMPLETED, FAILED, INTERRUPTED }

data class JobRow(
    val jobId: UUID,
    val ticketKey: String,
    val status: JobStatus,
    val executionName: String?
)

class JobRepository {

    suspend fun shouldDispatch(ticketKey: String): Boolean = withContext(Dispatchers.IO) {
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

    suspend fun insert(ticketKey: String, prompt: String): UUID = withContext(Dispatchers.IO) {
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

    suspend fun markRunning(jobId: UUID, executionName: String) = withContext(Dispatchers.IO) {
        transaction {
            JobsTable.update({ JobsTable.jobId eq jobId }) {
                it[JobsTable.status] = JobStatus.RUNNING.name
                it[JobsTable.executionName] = executionName
                it[JobsTable.startedAt] = Instant.now()
            }
        }
    }

    suspend fun markCompleted(jobId: UUID) = withContext(Dispatchers.IO) {
        transaction {
            JobsTable.update({ JobsTable.jobId eq jobId }) {
                it[JobsTable.status] = JobStatus.COMPLETED.name
                it[JobsTable.completedAt] = Instant.now()
            }
        }
    }

    suspend fun markFailed(jobId: UUID) = withContext(Dispatchers.IO) {
        transaction {
            JobsTable.update({ JobsTable.jobId eq jobId }) {
                it[JobsTable.status] = JobStatus.FAILED.name
                it[JobsTable.completedAt] = Instant.now()
            }
        }
    }

    suspend fun markInterrupted(jobId: UUID) = withContext(Dispatchers.IO) {
        transaction {
            JobsTable.update({ JobsTable.jobId eq jobId }) {
                it[JobsTable.status] = JobStatus.INTERRUPTED.name
                it[JobsTable.completedAt] = Instant.now()
            }
        }
    }

    suspend fun findRunningJobs(): List<JobRow> = withContext(Dispatchers.IO) {
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
}
