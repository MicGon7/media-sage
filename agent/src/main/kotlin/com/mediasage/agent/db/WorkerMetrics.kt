package com.mediasage.agent.db

/**
 * Token usage and cost captured from the Claude Code `result` event at the end of a worker run.
 *
 * All fields map directly to the stream-json `result` event:
 *   { "type": "result", "total_cost_usd": ..., "duration_ms": ..., "num_turns": ...,
 *     "usage": { "input_tokens": ..., "output_tokens": ...,
 *                "cache_read_input_tokens": ..., "cache_creation_input_tokens": ... } }
 *
 * Stored as nullable columns in the `jobs` table so that rows created before MS-210
 * and any run where log fetch fails degrade gracefully rather than failing the job.
 */
data class WorkerMetrics(
    val inputTokens: Int,
    val outputTokens: Int,
    val cacheReadTokens: Int,
    val cacheCreationTokens: Int,
    val totalCostUsd: Double,
    val durationMs: Long,
    val numTurns: Int
)
