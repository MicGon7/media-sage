package com.mediasage.analyst.di

import com.mediasage.analyst.db.FeedbackDatabase
import com.mediasage.analyst.scoring.ClaudeDecisionScorer
import com.mediasage.analyst.scoring.DecisionScorer
import com.mediasage.analyst.scoring.NoOpDecisionScorer
import com.mediasage.analyst.stats.JobsTableStatsReader
import com.mediasage.analyst.stats.PipelineStatsReader
import com.mediasage.pipeline.core.JobRegistry
import com.mediasage.pipeline.core.JobRepository
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import org.koin.dsl.module
import org.slf4j.LoggerFactory

private val log = LoggerFactory.getLogger("AnalystModule")

/**
 * Koin module for the Analyst feedback server.
 *
 * Wires the dependency graph for the reactive spine and decision-scoring layer:
 * - Verifies Supabase connectivity at startup (fail-fast on a bad `SUPABASE_DB_URL`).
 * - [JobRegistry] — the `:pipelineCore` repository, used by the Pub/Sub route to look up the
 *   job row for an incoming completion event.
 * - [PipelineStatsReader] — backs `GET /stats` with a real Supabase aggregation query.
 * - [DecisionScorer] — [ClaudeDecisionScorer] when `CLAUDE_API_KEY` is set; [NoOpDecisionScorer]
 *   otherwise (safe for environments without the API key configured).
 *
 * @param config Runtime configuration sourced from environment variables. See [AnalystConfig].
 */
fun analystModule(config: AnalystConfig) = module {
    initDatabase(config.supabaseDbUrl)
    single<JobRegistry> { JobRepository() }
    single<PipelineStatsReader> { JobsTableStatsReader() }
    single<DecisionScorer> {
        if (config.claudeApiKey.isNotBlank()) {
            val httpClient = HttpClient(OkHttp) {
                install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
            }
            log.info("Decision scoring enabled (ClaudeDecisionScorer)")
            ClaudeDecisionScorer(httpClient, config.claudeApiKey)
        } else {
            log.info("Decision scoring disabled — CLAUDE_API_KEY not set")
            NoOpDecisionScorer()
        }
    }
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
