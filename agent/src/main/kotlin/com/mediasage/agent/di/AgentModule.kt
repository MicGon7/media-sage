package com.mediasage.agent.di

import com.mediasage.agent.db.AgentDatabase
import com.mediasage.agent.db.JobRepository
import com.mediasage.agent.service.AgentBriefing
import com.mediasage.agent.service.AgentLaunchService
import com.mediasage.agent.service.CloudRunDispatch
import com.mediasage.agent.service.CloudRunJobsClient
import com.mediasage.agent.service.JiraApiService
import com.mediasage.agent.service.JiraCommentPoster
import com.mediasage.agent.service.JiraLabelChecker
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

fun agentModule(config: AgentConfig, scope: CoroutineScope) = module {
    single { buildHttpClient() }
    single { JiraApiService(get(), config.jiraCloudId, config.jiraEmail, config.jiraApiToken) }
    single<JiraLabelChecker> { get<JiraApiService>() }
    single<JiraTicketFetcher> { get<JiraApiService>() }
    single<JiraCommentPoster> { get<JiraApiService>() }
    single<JiraTicketStatusChecker> { get<JiraApiService>() }
    single {
        // CloudRunDispatch is resolved eagerly here so a startup failure (bad DB URL,
        // missing credentials) degrades gracefully to null rather than poisoning a
        // Koin nullable singleton — Koin 4 cannot store null as a singleton value.
        val cloudRun = try {
            buildCloudRunDispatch(config, get())
        } catch (e: Exception) {
            LoggerFactory.getLogger("AgentModule").warn("Cloud Run dispatch disabled: ${e.message}")
            null
        }
        val briefing = if (config.useCloudRunWorkers && config.agentBriefingEnabled) AgentBriefing(config.repoPath) else null
        AgentLaunchService(config.repoPath, scope, config.verboseLogging, cloudRun, get(), briefing, get<JiraTicketStatusChecker>())
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

private fun buildCloudRunDispatch(config: AgentConfig, httpClient: HttpClient): CloudRunDispatch? {
    if (!config.useCloudRunWorkers || config.googleCredentialsJson.isBlank()) return null
    if (config.supabaseDbUrl.isBlank()) return null
    AgentDatabase.init(config.supabaseDbUrl)
    val jobRepository = JobRepository()
    val client = CloudRunJobsClient(
        httpClient = httpClient,
        projectId = config.gcpProjectId,
        region = config.gcpRegion,
        jobName = config.gcpJobName,
        credentialsJson = config.googleCredentialsJson,
        jobRepository = jobRepository
    )
    return CloudRunDispatch(client, jobRepository)
}
