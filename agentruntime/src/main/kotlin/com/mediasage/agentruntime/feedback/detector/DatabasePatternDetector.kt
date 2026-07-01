package com.mediasage.agentruntime.feedback.detector

import com.mediasage.pipeline.core.DecisionScoresTable
import com.mediasage.pipeline.core.JobsTable
import org.jetbrains.exposed.sql.transactions.transaction
import org.slf4j.LoggerFactory

private val log = LoggerFactory.getLogger(DatabasePatternDetector::class.java)

class DatabasePatternDetector : PatternDetector {

    override fun detectPatterns(windowDays: Int, minOccurrences: Int): List<DetectedPattern> {
        val gates = detectGatePatterns(windowDays, minOccurrences)
        val scores = detectLowScorePatterns(windowDays, minOccurrences)
        log.debug(
            "Pattern scan (window={}d, min={}): {} gate pattern(s), {} low-score pattern(s)",
            windowDays, minOccurrences, gates.size, scores.size,
        )
        return gates + scores
    }

    private fun detectGatePatterns(windowDays: Int, minOccurrences: Int): List<DetectedPattern.GateFailure> {
        val sql = """
            SELECT failed_gate, COUNT(*) AS run_count
            FROM ${JobsTable.tableName}
            WHERE status = 'FAILED'
              AND failed_gate IS NOT NULL
              AND created_at >= now() - make_interval(days => $windowDays)
            GROUP BY failed_gate
            HAVING COUNT(*) >= $minOccurrences
            ORDER BY run_count DESC
        """.trimIndent()
        return transaction {
            exec(sql) { rs ->
                val results = mutableListOf<DetectedPattern.GateFailure>()
                while (rs.next()) {
                    results.add(
                        DetectedPattern.GateFailure(
                            gate = rs.getString("failed_gate"),
                            runCount = rs.getInt("run_count"),
                            windowDays = windowDays,
                        )
                    )
                }
                results
            } ?: emptyList()
        }
    }

    private fun detectLowScorePatterns(windowDays: Int, minOccurrences: Int): List<DetectedPattern.LowRubricScore> {
        val sql = """
            SELECT ds.criterion, AVG(ds.score) AS avg_score, COUNT(DISTINCT ds.job_id) AS run_count
            FROM ${DecisionScoresTable.tableName} ds
            JOIN ${JobsTable.tableName} j ON ds.job_id = j.job_id
            WHERE j.created_at >= now() - make_interval(days => $windowDays)
            GROUP BY ds.criterion
            HAVING AVG(ds.score) < 3.5 AND COUNT(DISTINCT ds.job_id) >= $minOccurrences
            ORDER BY avg_score ASC
        """.trimIndent()
        return transaction {
            exec(sql) { rs ->
                val results = mutableListOf<DetectedPattern.LowRubricScore>()
                while (rs.next()) {
                    results.add(
                        DetectedPattern.LowRubricScore(
                            criterion = rs.getString("criterion"),
                            avgScore = rs.getDouble("avg_score"),
                            runCount = rs.getInt("run_count"),
                            windowDays = windowDays,
                        )
                    )
                }
                results
            } ?: emptyList()
        }
    }
}
