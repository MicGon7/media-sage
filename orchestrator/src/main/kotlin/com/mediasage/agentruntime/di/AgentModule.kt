package com.mediasage.agentruntime.di

import com.mediasage.agentruntime.AnthropicClient
import com.mediasage.agentruntime.db.AgentDatabase
import com.mediasage.agentruntime.feedback.detector.DatabasePatternDetector
import com.mediasage.agentruntime.feedback.detector.PatternDetector
import com.mediasage.agentruntime.evaluation.AcComplianceEvaluator
import com.mediasage.agentruntime.evaluation.JudgingService
import com.mediasage.agentruntime.evaluation.NoOpAcComplianceEvaluator
import com.mediasage.agentruntime.evaluation.scoring.DecisionScorer
import com.mediasage.agentruntime.evaluation.scoring.NoOpScoringService
import com.mediasage.agentruntime.evaluation.scoring.ScoringService
import com.mediasage.agentruntime.feedback.github.GitHubApiClient
import com.mediasage.agentruntime.feedback.github.GitHubAppClient
import com.mediasage.agentruntime.feedback.pr.FeedbackPrService
import com.mediasage.pipeline.core.JobRepository
import com.mediasage.agentruntime.service.AgentLauncher
import com.mediasage.agentruntime.service.AgentLaunchService
import com.mediasage.agentruntime.service.CloudRunDispatch
import com.mediasage.agentruntime.service.CloudRunJobsClient
import com.mediasage.agentruntime.service.JiraApiService
import com.mediasage.agentruntime.service.JiraCommentPoster
import com.mediasage.agentruntime.service.JiraTicketFetcher
import com.mediasage.agentruntime.service.JiraTicketStatusChecker
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
        AgentLaunchService(scope, cloudRun, get(), get<JiraTicketStatusChecker>())
    }
    single<AgentLauncher> { get<AgentLaunchService>() }
}

private fun feedbackModule(config: AgentConfig) = module {
    single<PatternDetector> { DatabasePatternDetector() }
    if (isFeedbackEnabled(config)) {
        log.info("Feedback features enabled — repo={}/{}", config.githubRepoOwner, config.githubRepoName)
        single { AnthropicClient(get(), config.claudeAuthToken, config.claudeBaseUrl) }
        single<DecisionScorer> { ScoringService(get(), config.claudeModel) }
        single<GitHubApiClient> { buildGitHubApiClient(config, get()) }
        single { buildFeedbackPrService(config, get(), get(), get()) }
        single<AcComplianceEvaluator> { buildJudgingService(config, get(), get(), get(), get()) }
    } else {
        log.info("Feedback features disabled — GITHUB_APP_ID or ANTHROPIC_AUTH_TOKEN not configured")
        single<DecisionScorer> { NoOpScoringService() }
        single<AcComplianceEvaluator> { NoOpAcComplianceEvaluator() }
    }
}

private fun buildJiraCommentPoster(config: AgentConfig, httpClient: HttpClient, fallback: JiraApiService): JiraCommentPoster =
    if (config.jiraBotEmail.isNotBlank() && config.jiraBotApiToken.isNotBlank()) {
        JiraApiService(httpClient, config.jiraCloudId, config.jiraBotEmail, config.jiraBotApiToken)
    } else {
        fallback
    }

private fun buildGitHubApiClient(config: AgentConfig, httpClient: HttpClient): GitHubApiClient =
    GitHubAppClient(
        httpClient = httpClient,
        appId = config.githubAppId,
        privateKeyPem = config.githubAppPrivateKey,
        installationId = config.githubAppInstallationId,
    )

private fun buildFeedbackPrService(config: AgentConfig, httpClient: HttpClient, detector: PatternDetector, githubClient: GitHubApiClient) =
    FeedbackPrService(
        detector = detector,
        githubClient = githubClient,
        httpClient = httpClient,
        authToken = config.claudeAuthToken,
        claudeBaseUrl = config.claudeBaseUrl,
        repoOwner = config.githubRepoOwner,
        repoName = config.githubRepoName,
        model = config.claudeModel,
    )

private fun buildJudgingService(
    config: AgentConfig,
    anthropicClient: AnthropicClient,
    githubApiClient: GitHubApiClient,
    jiraTicketFetcher: JiraTicketFetcher,
    jiraCommentPoster: JiraCommentPoster,
) = JudgingService(
    anthropicClient = anthropicClient,
    githubApiClient = githubApiClient,
    jiraTicketFetcher = jiraTicketFetcher,
    jiraCommentPoster = jiraCommentPoster,
    model = config.claudeModel,
    repoOwner = config.githubRepoOwner,
    repoName = config.githubRepoName,
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
