package com.mediasage.analyst.stats

/**
 * Reads cross-run pipeline statistics over a trailing time window.
 *
 * Defined as an interface so routes depend on the capability, not the database. Production wires
 * [JobsTableStatsReader] (a real Supabase query); tests pass a lightweight fake that returns a
 * canned [RunStats] without a database.
 */
interface PipelineStatsReader {
    /**
     * Returns a [RunStats] summary over the last [windowDays] days of job history.
     *
     * @param windowDays Trailing window in days. Must be positive — the route validates this
     *   before calling.
     */
    suspend fun stats(windowDays: Int): RunStats
}
