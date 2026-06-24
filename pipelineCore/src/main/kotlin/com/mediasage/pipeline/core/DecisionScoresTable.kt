package com.mediasage.pipeline.core

import org.jetbrains.exposed.sql.Table

/**
 * Exposed table definition for the `decision_scores` table in Supabase Postgres.
 *
 * One row per (job, decision index, criterion). The [decisionIndex] field supports per-turn
 * granularity; the first implementation uses index 0 for an overall session-level score per
 * criterion.
 *
 * Schema is owned by the `:analyst` module. The orchestrator never reads or writes this table.
 */
object DecisionScoresTable : Table("decision_scores") {
    /** Job this score belongs to. */
    val jobId = uuid("job_id").references(JobsTable.jobId)

    /** Position of the decision within the session. 0 = overall session-level score. */
    val decisionIndex = integer("decision_index")

    /** Rubric criterion key (e.g. `tool_choice`, `retry_recovery`, `context_management`). */
    val criterion = text("criterion")

    /** Score 1–5, where 5 is best. */
    val score = integer("score")

    /** One-sentence explanation of the score from the Claude-as-judge call. */
    val rationale = text("rationale")

    override val primaryKey = PrimaryKey(jobId, decisionIndex, criterion)
}
