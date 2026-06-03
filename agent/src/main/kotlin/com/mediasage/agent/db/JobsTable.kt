package com.mediasage.agent.db

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.timestamp

/**
 * Exposed table definition for the `jobs` table in Supabase Postgres.
 *
 * Tracks every autonomous agent run from dispatch through completion. The orchestrator
 * inserts a row when a Cloud Run Job is dispatched and updates it as the job progresses
 * through the state machine: `PENDING → RUNNING → COMPLETED | FAILED | INTERRUPTED`.
 *
 * This table is also the persistent dedup gate — before dispatching, the orchestrator
 * queries the latest row for [ticketKey] and skips re-dispatch if the status is RUNNING
 * or COMPLETED.
 *
 * @property jobId Auto-generated UUID primary key.
 * @property ticketKey Jira ticket key (e.g. `MS-289`) that triggered this job.
 * @property prompt Full task prompt passed to the Cloud Run worker.
 * @property status Current lifecycle state. One of: PENDING, RUNNING, COMPLETED, FAILED, INTERRUPTED.
 * @property executionName Cloud Run execution name assigned at dispatch time. Used by
 *   `AgentLaunchService.recoverInterruptedJobs` to check whether an execution is still
 *   running after an orchestrator restart.
 * @property createdAt When the job row was first inserted (dispatch time).
 * @property startedAt When the Cloud Run Job execution actually started.
 * @property completedAt When the job reached a terminal state (COMPLETED or FAILED).
 * @property inputTokens (MS-210) Total input tokens consumed by the Claude Code session.
 * @property outputTokens (MS-210) Total output tokens produced by the Claude Code session.
 * @property cacheReadTokens (MS-210) Tokens served from the prompt cache (reduces cost).
 * @property cacheCreationTokens (MS-210) Tokens written to the prompt cache.
 * @property totalCostUsd (MS-210) Estimated USD cost of the session, to six decimal places.
 * @property claudeDurationMs (MS-210) Wall-clock duration of the Claude Code session in milliseconds.
 * @property numTurns (MS-210) Number of conversation turns in the Claude Code session.
 *
 * The MS-210 fields are populated from the Claude Code `result` event via Cloud Logging after
 * job completion. All are nullable so rows created before MS-210 are unaffected.
 */
object JobsTable : Table("jobs") {
    val jobId = uuid("job_id").autoGenerate()
    val ticketKey = text("ticket_key")
    val prompt = text("prompt")
    val status = text("status").default("PENDING")
    val executionName = text("execution_name").nullable()
    val createdAt = timestamp("created_at")
    val startedAt = timestamp("started_at").nullable()
    val completedAt = timestamp("completed_at").nullable()
    val inputTokens = integer("input_tokens").nullable()
    val outputTokens = integer("output_tokens").nullable()
    val cacheReadTokens = integer("cache_read_tokens").nullable()
    val cacheCreationTokens = integer("cache_creation_tokens").nullable()
    val totalCostUsd = decimal("total_cost_usd", precision = 10, scale = 6).nullable()
    val claudeDurationMs = long("claude_duration_ms").nullable()
    val numTurns = integer("num_turns").nullable()

    override val primaryKey = PrimaryKey(jobId)
}
