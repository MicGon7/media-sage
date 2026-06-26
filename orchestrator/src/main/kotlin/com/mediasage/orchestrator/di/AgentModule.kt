package com.mediasage.orchestrator.di

import com.mediasage.orchestrator.db.AgentDatabase
import com.mediasage.orchestrator.feedback.detector.DatabasePatternDetector
import com.mediasage.orchestrator.feedback.detector.PatternDetector
import com.mediasage.orchestrator.feedback.github.GitHubAppClient
import com.mediasage.orchestrator.feedback.pr.ClaudeCallParams
import com.mediasage.orchestrator.feedback.pr.FeedbackPrService
import com.mediasage.orchestrator.feedback.scoring.ClaudeDecisionScorer
import com.mediasage.orchestrator.feedback.scoring.DecisionScorer
import com.mediasage.orchestrator.feedback.scoring.NoOpDecisionScorer
import com.mediasage.pipeline.core.JobRepository
import com.mediasage.orchestrator.service.AgentLauncher
import com.mediasage.orchestrator.service.AgentLaunchService
import com.mediasage.orchestrator.service.CloudRunDispatch
import com.mediasage.orchestrator.service.CloudRunJobsClient
import com.mediasage.orchestrator.service.JiraApiService
import com.mediasage.orchestrator.service.JiraCommentPoster
import com.mediasage.orchestrator.service.JiraTicketFetcher
import com.mediasage.orchestrator.service.JiraTicketStatusChecker
import io.ktor.client.*
import io.ktor.client.engine.okhttp.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.serialization.json.Json
import org.koin.dsl.module
import org.slf4j.LoggerFactory

/**
 * Koin module for the agent orchestration server.
 *
 * Registers the full dependency graph for webhook routing and Cloud Run Job dispatch:
 * - [HttpClient] — OkHttp client with JSON content negotiation and request timeouts.
 * - [JiraApiService] — bound to the [JiraTicketFetcher] and
 *   [JiraTicketStatusChecker] interfaces using human account credentials.
 * - [JiraCommentPoster] — uses bot credentials when configured, falling back to human credentials.
 * - [AgentLaunchService] — orchestrates Cloud Run Job dispatch and performs job recovery on startup.
 * - [AgentLauncher] — interface alias for [AgentLaunchService].
 *
 * @param config Runtime configuration sourced from environment variables. See [AgentConfig].
 * @param scope Coroutine scope used by [AgentLaunchService] for background startup job recovery.
 */
private val log = LoggerFactory.getLogger("AgentModule")

fun agentModule(config: AgentConfig, scope: CoroutineScope) = module {
    includes(feedbackModule(config))
    single { buildHttpClient() }
    single { JiraApiService(get(), config.jiraCloudId, config.jiraEmail, config.jiraApiToken) }
    single<JiraTicketFetcher> { get<JiraApiService>() }
    single<JiraTicketStatusChecker> { get<JiraApiService>() }
    single<JiraCommentPoster> { buildJiraCommentPoster(config, get(), get()) }
    single {
        val cloudRun = buildCloudRunDispatch(config, get())
        AgentLaunchService(
            scope, cloudRun, get(), get<JiraTicketStatusChecker>(),
            judgeJobName = config.gcpJudgeJobName,
        )
    }
    single<AgentLauncher> { get<AgentLaunchService>() }
}

private fun feedbackModule(config: AgentConfig) = module {
    single<DecisionScorer> { buildDecisionScorer(config, get()) }
    single<PatternDetector> { DatabasePatternDetector() }
    if (isFeedbackEnabled(config)) {
        log.info("Feedback auto-PR enabled — repo={}/{}", config.githubRepoOwner, config.githubRepoName)
        single { buildFeedbackPrService(config, get(), get()) }
    } else {
        log.info("Feedback auto-PR disabled — GITHUB_APP_ID or ANTHROPIC_AUTH_TOKEN not configured")
    }
}

private fun buildJiraCommentPoster(config: AgentConfig, httpClient: HttpClient, fallback: JiraApiService): JiraCommentPoster =
    if (config.jiraBotEmail.isNotBlank() && config.jiraBotApiToken.isNotBlank()) {
        JiraApiService(httpClient, config.jiraCloudId, config.jiraBotEmail, config.jiraBotApiToken)
    } else {
        fallback
    }

private fun buildDecisionScorer(config: AgentConfig, httpClient: HttpClient): DecisionScorer =
    if (config.claudeAuthToken.isNotBlank()) {
        log.info("Decision scoring enabled — baseUrl={}", config.claudeBaseUrl)
        ClaudeDecisionScorer(
            httpClient = httpClient,
            authToken = config.claudeAuthToken,
            baseUrl = config.claudeBaseUrl,
            model = config.claudeModel,
            apiVersion = config.claudeApiVersion,
            maxTokens = config.claudeMaxTokensScoring,
        )
    } else {
        log.info("Decision scoring disabled — ANTHROPIC_AUTH_TOKEN not set")
        NoOpDecisionScorer()
    }

private fun buildFeedbackPrService(config: AgentConfig, httpClient: HttpClient, detector: PatternDetector) =
    FeedbackPrService(
        detector = detector,
        githubClient = GitHubAppClient(
            httpClient = httpClient,
            appId = config.githubAppId,
            privateKeyPem = config.githubAppPrivateKey,
            installationId = config.githubAppInstallationId,
        ),
        httpClient = httpClient,
        authToken = config.claudeAuthToken,
        claudeBaseUrl = config.claudeBaseUrl,
        repoOwner = config.githubRepoOwner,
        repoName = config.githubRepoName,
        claude = ClaudeCallParams(config.claudeModel, config.claudeApiVersion, config.claudeMaxTokensSynthesis),
    )

private fun isFeedbackEnabled(config: AgentConfig): Boolean =
    listOf(config.githubAppId, config.githubAppPrivateKey, config.githubAppInstallationId, config.githubRepoOwner, config.githubRepoName)
        .all { it.isNotBlank() } && config.claudeAuthToken.isNotBlank()

private fun buildHttpClient() = HttpClient(OkHttp) {
    install(ContentNegotiation) {
        json(Json { prettyPrint = false; ignoreUnknownKeys = true })
    }
    install(HttpTimeout) {
        requestTimeoutMillis = 60_000
        connectTimeoutMillis = 10_000
        socketTimeoutMillis = 60_000
    }
}

private fun initDatabase(supabaseDbUrl: String) {
    if (supabaseDbUrl.isBlank()) {
        log.error("SUPABASE_DB_URL is not set — verify environment configuration and restart")
        kotlin.system.exitProcess(1)
    }
    try {
        AgentDatabase.init(supabaseDbUrl)
        log.info("Supabase DB connectivity verified")
    } catch (e: Exception) {
        log.error("Failed to connect to Supabase database — verify SUPABASE_DB_URL is set correctly", e)
        kotlin.system.exitProcess(1)
    }
}

private fun buildCloudRunDispatch(
    config: AgentConfig,
    httpClient: HttpClient,
): CloudRunDispatch? {
    if (config.googleCredentialsJson.isBlank()) error("GOOGLE_CREDENTIALS_BASE64 is required — Cloud Run is the only worker dispatch path")
    initDatabase(config.supabaseDbUrl)
    val jobRepository = JobRepository()
    val client = CloudRunJobsClient(
        httpClient = httpClient,
        projectId = config.gcpProjectId,
        region = config.gcpRegion,
        jobName = config.gcpJobName,
        credentialsJson = config.googleCredentialsJson,
        jobRepository = jobRepository,
    )
    return CloudRunDispatch(client, jobRepository)
}
