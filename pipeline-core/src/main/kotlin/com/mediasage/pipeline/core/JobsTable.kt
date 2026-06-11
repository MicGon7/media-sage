package com.mediasage.pipeline.core

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.timestamp

/**
 * Exposed table definition for the `jobs` table in Supabase Postgres.
 *
 * Tracks every Cloud Run Job dispatch: lifecycle state, Cloud Run execution identity,
 * and post-completion worker efficiency metrics. One row per dispatch attempt; retries
 * produce a new row rather than updating the previous one.
 *
 * Status state machine: `PENDING → RUNNING → COMPLETED | FAILED | INTERRUPTED`
 */
object JobsTable : Table("jobs") {
    /** Auto-generated UUID primary key. */
    val jobId = uuid("job_id").autoGenerate()

    /** Jira ticket key (e.g. `MS-123`) that triggered this job. */
    val ticketKey = text("ticket_key")

    /** Bootstrap prompt passed to the Claude Code worker at dispatch time. */
    val prompt = text("prompt")

    /** Current lifecycle status. Defaults to `PENDING`; see the state machine above. */
    val status = text("status").default("PENDING")

    /** Cloud Run execution name assigned at dispatch (e.g. `media-sage-agent-worker-abc12`). Null until dispatched. */
    val executionName = text("execution_name").nullable()

    /** Timestamp when the row was inserted (job enqueued). */
    val createdAt = timestamp("created_at")

    /** Timestamp when the Cloud Run Job execution started. Null until the job begins running. */
    val startedAt = timestamp("started_at").nullable()

    /** Timestamp when the job reached a terminal state (COMPLETED, FAILED, or INTERRUPTED). */
    val completedAt = timestamp("completed_at").nullable()

    // Worker efficiency metrics (MS-210) — populated from the Claude Code `result` event
    // via Cloud Logging after job completion. Nullable so pre-MS-210 rows are unaffected.

    /** Total input tokens consumed by the Claude Code worker session. */
    val inputTokens = integer("input_tokens").nullable()

    /** Total output tokens produced by the Claude Code worker session. */
    val outputTokens = integer("output_tokens").nullable()

    /** Prompt-cache read tokens (cache hits) during the worker session. */
    val cacheReadTokens = integer("cache_read_tokens").nullable()

    /** Prompt-cache creation tokens (cache writes) during the worker session. */
    val cacheCreationTokens = integer("cache_creation_tokens").nullable()

    /** Estimated USD cost of the worker session, stored with microsecond precision. */
    val totalCostUsd = decimal("total_cost_usd", precision = 10, scale = 6).nullable()

    /** Wall-clock duration of the Claude Code session in milliseconds. */
    val claudeDurationMs = long("claude_duration_ms").nullable()

    /** Number of agentic turns (tool-use + response cycles) in the worker session. */
    val numTurns = integer("num_turns").nullable()

    override val primaryKey = PrimaryKey(jobId)
}
