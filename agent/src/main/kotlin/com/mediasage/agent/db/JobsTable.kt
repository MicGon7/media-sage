package com.mediasage.agent.db

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.timestamp

object JobsTable : Table("jobs") {
    val jobId = uuid("job_id").autoGenerate()
    val ticketKey = text("ticket_key")
    val prompt = text("prompt")
    val status = text("status").default("PENDING")
    val executionName = text("execution_name").nullable()
    val createdAt = timestamp("created_at")
    val startedAt = timestamp("started_at").nullable()
    val completedAt = timestamp("completed_at").nullable()

    override val primaryKey = PrimaryKey(jobId)
}
