package com.mediasage.analyst.stats

import kotlinx.serialization.Serializable

/**
 * A rubric criterion that scores below threshold across multiple recent runs, indicating a
 * recurring pattern worth surfacing in the `GET /stats` response and citing in auto-PR rationale
 * (MS-389).
 *
 * @property criterion Rubric criterion key (e.g. `tool_choice`, `retry_recovery`).
 * @property avgScore Average score (1–5) across [runCount] recent runs. Values below 3.5 are
 *   surfaced as patterns; lower is worse.
 * @property runCount Number of distinct jobs that contributed to [avgScore] in the query window.
 */
@Serializable
data class LowScorePattern(
    val criterion: String,
    val avgScore: Double,
    val runCount: Int,
)
