package com.mediasage.agent.di

import com.mediasage.agent.db.AgentDatabase
import com.mediasage.agent.db.JobRepository
import com.mediasage.agent.service.AgentLauncher
import com.mediasage.agent.service.AgentLaunchService
import com.mediasage.agent.service.BriefingService
import com.mediasage.agent.service.HaikuBriefingService
import com.mediasage.agent.service.CloudLoggingClient
import com.mediasage.agent.service.CloudRunDispatch
import com.mediasage.agent.service.CloudRunJobsClient
import com.mediasage.agent.service.JiraApiService
import com.mediasage.agent.service.JiraCommentPoster
import com.mediasage.agent.service.JiraTicketFetcher
import com.mediasage.agent.service.JiraTicketStatusChecker
import io.ktor.client.*
import io.ktor.client.engine.okhttp.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.serialization.json.Json
import org.koin.dsl.module

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
fun agentModule(config: AgentConfig, scope: CoroutineScope) = module {
    single { buildHttpClient() }
    single { JiraApiService(get(), config.jiraCloudId, config.jiraEmail, config.jiraApiToken) }
    single<JiraTicketFetcher> { get<JiraApiService>() }
    single<JiraTicketStatusChecker> { get<JiraApiService>() }
    // Bot credentials for posting automated comments — falls back to human credentials if not configured.
    single<JiraCommentPoster> {
        val botEmail = config.jiraBotEmail
        val botToken = config.jiraBotApiToken
        if (botEmail.isNotBlank() && botToken.isNotBlank()) {
            JiraApiService(get(), config.jiraCloudId, botEmail, botToken)
        } else {
            get<JiraApiService>()
        }
    }
    single {
        val briefingService = if (config.intelligentDispatchEnabled && config.anthropicAuthToken.isNotBlank()) {
            HaikuBriefingService(buildBriefingHttpClient(), config.anthropicBaseUrl, config.anthropicAuthToken)
        } else null
        val cloudRun = buildCloudRunDispatch(config, get(), get())
        AgentLaunchService(config.repoPath, scope, cloudRun, get(), get<JiraTicketStatusChecker>(), briefingService)
    }
    single<AgentLauncher> { get<AgentLaunchService>() }
}

// Dedicated client for briefing calls — tighter 5s timeout so a slow Claude API
// response never delays dispatch beyond the briefing budget.
private fun buildBriefingHttpClient() = HttpClient(OkHttp) {
    install(ContentNegotiation) {
        json(Json { prettyPrint = false; ignoreUnknownKeys = true })
    }
    install(HttpTimeout) {
        requestTimeoutMillis = 5_000
        connectTimeoutMillis = 5_000
        socketTimeoutMillis = 5_000
    }
}

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

private fun buildCloudRunDispatch(
    config: AgentConfig,
    httpClient: HttpClient,
    jiraCommentPoster: JiraCommentPoster
): CloudRunDispatch? {
    if (config.googleCredentialsJson.isBlank()) error("GOOGLE_CREDENTIALS_BASE64 is required — Cloud Run is the only worker dispatch path")
    if (config.supabaseDbUrl.isBlank()) error("SUPABASE_DB_URL is required — job registry must be configured")
    AgentDatabase.init(config.supabaseDbUrl)
    val jobRepository = JobRepository()
    val loggingClient = CloudLoggingClient(
        httpClient = httpClient,
        projectId = config.gcpProjectId,
        credentialsJson = config.googleCredentialsJson
    )
    val client = CloudRunJobsClient(
        httpClient = httpClient,
        projectId = config.gcpProjectId,
        region = config.gcpRegion,
        jobName = config.gcpJobName,
        credentialsJson = config.googleCredentialsJson,
        jobRepository = jobRepository,
        cloudLoggingClient = loggingClient,
        jiraCommentPoster = jiraCommentPoster
    )
    return CloudRunDispatch(client, jobRepository)
}
