package com.mediasage.analyst.di

import com.mediasage.analyst.db.FeedbackDatabase
import com.mediasage.analyst.detector.DatabasePatternDetector
import com.mediasage.analyst.detector.PatternDetector
import com.mediasage.analyst.github.GitHubAppClient
import com.mediasage.analyst.pr.SkillPrService
import com.mediasage.analyst.scoring.ClaudeDecisionScorer
import com.mediasage.analyst.scoring.DecisionScorer
import com.mediasage.analyst.scoring.NoOpDecisionScorer
import com.mediasage.analyst.stats.JobsTableStatsReader
import com.mediasage.analyst.stats.PipelineStatsReader
import com.mediasage.pipeline.core.JobRegistry
import com.mediasage.pipeline.core.JobRepository
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import org.koin.dsl.module
import org.slf4j.LoggerFactory

private val log = LoggerFactory.getLogger("AnalystModule")

/**
 * Koin module for the Analyst feedback server.
 *
 * Wires the dependency graph for the reactive spine, decision-scoring, and auto-PR layers:
 * - Verifies Supabase connectivity at startup (fail-fast on a bad `SUPABASE_DB_URL`).
 * - [JobRegistry] — the `:pipelineCore` repository for Pub/Sub job lookups.
 * - [PipelineStatsReader] — backs `GET /stats` with Supabase aggregation queries.
 * - [DecisionScorer] — [ClaudeDecisionScorer] when `ANTHROPIC_AUTH_TOKEN` is set; [NoOpDecisionScorer]
 *   otherwise.
 * - [PatternDetector] — [DatabasePatternDetector] backed by Supabase.
 * - [SkillPrService] — wired when all five `GITHUB_*` env vars are present; null otherwise
 *   (auto-PR feature silently disabled).
 *
 * @param config Runtime configuration sourced from environment variables. See [AnalystConfig].
 */
@Suppress("LongMethod")
fun analystModule(config: AnalystConfig) = module {
    initDatabase(config.supabaseDbUrl)

    single<HttpClient> {
        HttpClient(OkHttp) {
            install(HttpTimeout) { requestTimeoutMillis = 60_000; socketTimeoutMillis = 60_000 }
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        }
    }

    single<JobRegistry> { JobRepository() }
    single<PipelineStatsReader> { JobsTableStatsReader() }

    single<DecisionScorer> {
        if (config.claudeAuthToken.isNotBlank()) {
            log.info("Decision scoring enabled (ClaudeDecisionScorer) — baseUrl={}", config.claudeBaseUrl)
            ClaudeDecisionScorer(get(), config.claudeAuthToken, config.claudeBaseUrl)
        } else {
            log.info("Decision scoring disabled — ANTHROPIC_AUTH_TOKEN not set")
            NoOpDecisionScorer()
        }
    }

    single<PatternDetector> { DatabasePatternDetector() }

    if (isGithubConfigured(config)) {
        log.info("Auto-PR enabled (SkillPrService) — repo={}/{}", config.githubRepoOwner, config.githubRepoName)
        single<SkillPrService> {
            SkillPrService(
                detector = get(),
                githubClient = GitHubAppClient(
                    httpClient = get(),
                    appId = config.githubAppId,
                    privateKeyPem = config.githubPrivateKey,
                    installationId = config.githubInstallationId,
                ),
                httpClient = get(),
                authToken = config.claudeAuthToken,
                claudeBaseUrl = config.claudeBaseUrl,
                repoOwner = config.githubRepoOwner,
                repoName = config.githubRepoName,
            )
        }
    } else {
        log.info("Auto-PR disabled — GITHUB_* env vars or ANTHROPIC_AUTH_TOKEN not fully configured")
    }
}

private fun isGithubConfigured(config: AnalystConfig): Boolean =
    listOf(config.githubAppId, config.githubPrivateKey, config.githubInstallationId, config.githubRepoOwner, config.githubRepoName)
        .all { it.isNotBlank() } && config.claudeAuthToken.isNotBlank()

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
