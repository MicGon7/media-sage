package com.mediasage.agentruntime.feedback.detector

import com.mediasage.pipeline.core.JobsTable
import org.jetbrains.exposed.sql.transactions.transaction
import org.slf4j.LoggerFactory

private val log = LoggerFactory.getLogger(DatabasePatternDetector::class.java)

class DatabasePatternDetector : PatternDetector {

    override fun detectPatterns(windowDays: Int, minOccurrences: Int): List<DetectedPattern> {
        val gates = detectGatePatterns(windowDays, minOccurrences)
        log.debug(
            "Pattern scan (window={}d, min={}): {} gate pattern(s)",
            windowDays, minOccurrences, gates.size,
        )
        return gates
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
}
