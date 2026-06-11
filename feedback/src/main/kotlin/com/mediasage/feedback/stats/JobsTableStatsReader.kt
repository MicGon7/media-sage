package com.mediasage.feedback.stats

import com.mediasage.pipeline.core.JobsTable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.exposed.sql.transactions.transaction
import java.sql.ResultSet

/**
 * Supabase Postgres implementation of [PipelineStatsReader].
 *
 * Aggregates the shared `jobs` table (schema owned by `:pipeline-core`) in a single SQL pass.
 * Postgres `FILTER` clauses scope each aggregate to the right subset of rows — pass rate over
 * terminal runs, average cost over completed runs — so the whole summary is one round trip.
 *
 * [windowDays] is interpolated directly into the SQL. This is injection-safe because the route
 * validates it as a positive `Int` before this method is reached; no string input is ever
 * concatenated into the query.
 *
 * Runs on [Dispatchers.IO] with a blocking Exposed transaction, matching the pattern in
 * `JobRepository` — Exposed's transaction DSL is synchronous and must not run on the default
 * coroutine dispatcher.
 */
class JobsTableStatsReader : PipelineStatsReader {

    override suspend fun stats(windowDays: Int): RunStats = withContext(Dispatchers.IO) {
        val sql = """
            SELECT
              COUNT(*) AS total_runs,
              COUNT(*) FILTER (WHERE status = 'COMPLETED') AS completed_runs,
              COUNT(*) FILTER (WHERE status IN ('COMPLETED', 'FAILED', 'INTERRUPTED')) AS terminal_runs,
              AVG(total_cost_usd) FILTER (WHERE status = 'COMPLETED') AS avg_cost,
              AVG(EXTRACT(EPOCH FROM (completed_at - started_at)))
                FILTER (WHERE completed_at IS NOT NULL AND started_at IS NOT NULL) AS avg_wall_seconds,
              AVG(num_turns) FILTER (WHERE num_turns IS NOT NULL) AS avg_turns
            FROM ${JobsTable.tableName}
            WHERE created_at >= now() - make_interval(days => $windowDays)
        """.trimIndent()
        transaction {
            exec(sql) { rs ->
                if (rs.next()) mapRow(rs, windowDays) else RunStats.empty(windowDays)
            } ?: RunStats.empty(windowDays)
        }
    }

    /** Maps the single aggregate row returned by the stats query into a [RunStats]. */
    private fun mapRow(rs: ResultSet, windowDays: Int): RunStats {
        val completed = rs.getInt("completed_runs")
        val terminal = rs.getInt("terminal_runs")
        return RunStats(
            windowDays = windowDays,
            totalRuns = rs.getInt("total_runs"),
            completedRuns = completed,
            terminalRuns = terminal,
            passRate = if (terminal > 0) completed.toDouble() / terminal else 0.0,
            avgCostUsd = rs.getDouble("avg_cost").takeIf { !rs.wasNull() },
            avgWallClockSeconds = rs.getDouble("avg_wall_seconds").takeIf { !rs.wasNull() },
            avgTurns = rs.getDouble("avg_turns").takeIf { !rs.wasNull() },
        )
    }
}
