package com.mediasage.agent.di

import com.mediasage.agent.db.AgentDatabase
import com.mediasage.pipeline.core.JobRepository
import com.mediasage.agent.service.AgentLauncher
import com.mediasage.agent.service.AgentLaunchService
import com.mediasage.agent.service.BriefingService
import com.mediasage.agent.service.HttpBriefingService
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
        val briefingService = buildBriefingService(config)
        val cloudRun = buildCloudRunDispatch(config, get())
        AgentLaunchService(
            scope, cloudRun, get(), get<JiraTicketStatusChecker>(), briefingService,
            judgeJobName = config.gcpJudgeJobName,
            commentJobName = config.gcpCommentJobName,
        )
    }
    single<AgentLauncher> { get<AgentLaunchService>() }
}

private fun buildBriefingService(config: AgentConfig): HttpBriefingService? {
    if (config.intelligentDispatchEnabled && config.anthropicAuthToken.isNotBlank()) {
        log.info("Intelligent dispatch enabled — BriefingService active (base: ${config.anthropicBaseUrl})")
        return HttpBriefingService(buildBriefingHttpClient(), config.anthropicBaseUrl, config.anthropicAuthToken)
    }
    log.warn(
        "Intelligent dispatch disabled — BriefingService not active " +
        "(intelligentDispatchEnabled=${config.intelligentDispatchEnabled}, " +
        "anthropicAuthToken=${if (config.anthropicAuthToken.isBlank()) "BLANK" else "set"})"
    )
    return null
}

// Dedicated client for briefing calls — 15s timeout gives Haiku room to process
// large diffs without blocking dispatch indefinitely. The webhook has already
// returned 200 by this point, so this only affects time-to-dispatch, not latency.
private fun buildBriefingHttpClient() = HttpClient(OkHttp) {
    install(ContentNegotiation) {
        json(Json { prettyPrint = false; ignoreUnknownKeys = true })
    }
    install(HttpTimeout) {
        requestTimeoutMillis = 15_000
        connectTimeoutMillis = 5_000
        socketTimeoutMillis = 15_000
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
    )
    return CloudRunDispatch(client, jobRepository)
}
