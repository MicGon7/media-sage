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

    // MS-210: Worker efficiency metrics — populated from the Claude Code `result` event
    // via Cloud Logging after job completion. Nullable so pre-MS-210 rows are unaffected.
    val inputTokens = integer("input_tokens").nullable()
    val outputTokens = integer("output_tokens").nullable()
    val cacheReadTokens = integer("cache_read_tokens").nullable()
    val cacheCreationTokens = integer("cache_creation_tokens").nullable()
    val totalCostUsd = decimal("total_cost_usd", precision = 10, scale = 6).nullable()
    val claudeDurationMs = long("claude_duration_ms").nullable()
    val numTurns = integer("num_turns").nullable()

    override val primaryKey = PrimaryKey(jobId)
}
