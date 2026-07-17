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

    /** Compact JSON of the job identifiers dispatched to the worker (e.g. `{"ticketKey":"MS-123"}`). */
    val payload = text("payload")

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

    // Worker efficiency metrics — populated from the Claude Code `result` event via Cloud Logging
    // after job completion. Nullable so rows written before these columns existed are unaffected.

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

    // Model tracking. Nullable so older rows and runs where the value is unavailable degrade
    // gracefully.
    //
    // A sibling `failed_gate` column was retired: run death (`status = FAILED`) is not a gate
    // failure, and the hardened pipeline suppresses gate failures by design, so the column was
    // never populated. See docs/MS-386-jobs-failure-attribution-model.md for the full rationale.

    /**
     * Claude model that ran the worker session (e.g. `claude-sonnet-4-5-20250929`),
     * sourced from the `modelUsage` key of the Claude Code `result` event. Null when unavailable.
     */
    val modelVersion = text("model_version").nullable()

    override val primaryKey = PrimaryKey(jobId)
}
