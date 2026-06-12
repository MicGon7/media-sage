package com.mediasage.feedback.stats

import kotlinx.serialization.Serializable

/**
 * Cross-run summary of pipeline health over a trailing time window.
 *
 * Computed by [PipelineStatsReader] over the shared `jobs` table and returned by the Analyst's
 * `GET /stats` endpoint. All averages are nullable because a window may contain no qualifying
 * rows (e.g. no completed runs yet → [avgCostUsd] is null rather than a misleading 0.0).
 *
 * @property windowDays Trailing window the summary covers, in days.
 * @property totalRuns Count of every job row created within the window, regardless of status.
 * @property completedRuns Count of rows in the COMPLETED state.
 * @property terminalRuns Count of rows that reached a terminal state (COMPLETED, FAILED, or
 *   INTERRUPTED). PENDING and RUNNING rows are excluded — they have no outcome yet.
 * @property passRate [completedRuns] / [terminalRuns], in the range 0.0..1.0. Zero when no run
 *   has reached a terminal state in the window.
 * @property avgCostUsd Average estimated USD cost across COMPLETED runs, or null if none.
 * @property avgWallClockSeconds Average wall-clock duration (started → completed) across terminal
 *   runs with both timestamps, or null if none.
 * @property avgTurns Average number of agentic turns across runs that reported a turn count, or
 *   null if none.
 */
@Serializable
data class RunStats(
    val windowDays: Int,
    val totalRuns: Int,
    val completedRuns: Int,
    val terminalRuns: Int,
    val passRate: Double,
    val avgCostUsd: Double? = null,
    val avgWallClockSeconds: Double? = null,
    val avgTurns: Double? = null,
) {
    companion object {
        /** A zeroed summary for a window with no job rows. */
        fun empty(windowDays: Int) = RunStats(
            windowDays = windowDays,
            totalRuns = 0,
            completedRuns = 0,
            terminalRuns = 0,
            passRate = 0.0,
        )
    }
}
