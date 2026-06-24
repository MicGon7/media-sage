package com.mediasage.analyst.stats

import com.mediasage.pipeline.core.JobsTable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.exposed.sql.transactions.transaction
import java.sql.ResultSet

/**
 * Supabase Postgres implementation of [PipelineStatsReader].
 *
 * Aggregates the shared `jobs` table (schema owned by `:pipelineCore`) plus the `decision_scores`
 * table (schema owned by `:analyst`) in two queries per window. Postgres `FILTER` clauses scope
 * each aggregate to the right subset of rows.
 *
 * [windowDays] is interpolated directly into the SQL. This is injection-safe because the route
 * validates it as a positive `Int` before this method is reached; no string input is ever
 * concatenated into the query.
 *
 * Runs on [Dispatchers.IO] with blocking Exposed transactions, matching the pattern in
 * `JobRepository` — Exposed's transaction DSL is synchronous and must not run on the default
 * coroutine dispatcher.
 */
class JobsTableStatsReader : PipelineStatsReader {

    override suspend fun stats(windowDays: Int): RunStats = withContext(Dispatchers.IO) {
        val base = queryBaseStats(windowDays)
        val patterns = queryLowScorePatterns(windowDays)
        base.copy(lowScorePatterns = patterns.ifEmpty { null })
    }

    private fun queryBaseStats(windowDays: Int): RunStats {
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
        return transaction {
            exec(sql) { rs ->
                if (rs.next()) mapBaseRow(rs, windowDays) else RunStats.empty(windowDays)
            } ?: RunStats.empty(windowDays)
        }
    }

    private fun queryLowScorePatterns(windowDays: Int): List<LowScorePattern> {
        val sql = """
            SELECT ds.criterion, AVG(ds.score) AS avg_score, COUNT(DISTINCT ds.job_id) AS run_count
            FROM decision_scores ds
            JOIN ${JobsTable.tableName} j ON ds.job_id = j.job_id
            WHERE j.created_at >= now() - make_interval(days => $windowDays)
            GROUP BY ds.criterion
            HAVING AVG(ds.score) < 3.5
            ORDER BY avg_score ASC
        """.trimIndent()
        return transaction {
            exec(sql) { rs ->
                val results = mutableListOf<LowScorePattern>()
                while (rs.next()) results.add(mapPatternRow(rs))
                results
            } ?: emptyList()
        }
    }

    private fun mapBaseRow(rs: ResultSet, windowDays: Int): RunStats {
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

    private fun mapPatternRow(rs: ResultSet) = LowScorePattern(
        criterion = rs.getString("criterion"),
        avgScore = rs.getDouble("avg_score"),
        runCount = rs.getInt("run_count"),
    )
}
