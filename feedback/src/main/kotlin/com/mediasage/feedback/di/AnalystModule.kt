package com.mediasage.feedback.di

import com.mediasage.feedback.db.FeedbackDatabase
import com.mediasage.feedback.stats.JobsTableStatsReader
import com.mediasage.feedback.stats.PipelineStatsReader
import com.mediasage.pipeline.core.JobRegistry
import com.mediasage.pipeline.core.JobRepository
import org.koin.dsl.module
import org.slf4j.LoggerFactory

private val log = LoggerFactory.getLogger("AnalystModule")

/**
 * Koin module for the Analyst feedback server.
 *
 * Wires the minimal dependency graph for the reactive spine:
 * - Verifies Supabase connectivity at startup (fail-fast on a bad `SUPABASE_DB_URL`).
 * - [JobRegistry] — the `:pipeline-core` repository, used by the Pub/Sub route to look up the
 *   job row for an incoming completion event.
 * - [PipelineStatsReader] — backs `GET /stats` with a real Supabase aggregation query.
 *
 * @param config Runtime configuration sourced from environment variables. See [AnalystConfig].
 */
fun analystModule(config: AnalystConfig) = module {
    initDatabase(config.supabaseDbUrl)
    single<JobRegistry> { JobRepository() }
    single<PipelineStatsReader> { JobsTableStatsReader() }
}

private fun initDatabase(supabaseDbUrl: String) {
    if (supabaseDbUrl.isBlank()) {
        log.error("SUPABASE_DB_URL is not set — verify environment configuration and restart")
        kotlin.system.exitProcess(1)
    }
    try {
        FeedbackDatabase.init(supabaseDbUrl)
        log.info("Supabase DB connectivity verified")
    } catch (e: Exception) {
        log.error("Failed to connect to Supabase database — verify SUPABASE_DB_URL is set correctly", e)
        kotlin.system.exitProcess(1)
    }
}
