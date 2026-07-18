package com.mediasage.agentruntime

import com.mediasage.agentruntime.di.AgentConfig
import com.mediasage.agentruntime.di.agentModule
import com.mediasage.agentruntime.evaluation.AgentService
import com.mediasage.agentruntime.plugins.*
import com.mediasage.agentruntime.routes.githubWebhookRoutes
import com.mediasage.agentruntime.routes.PostCompletionActions
import com.mediasage.agentruntime.routes.pubSubWebhookRoutes
import com.mediasage.agentruntime.routes.webhookRoutes
import com.mediasage.agentruntime.service.AgentLaunchService
import com.mediasage.agentruntime.service.JobCompletionNotifier
import com.mediasage.pipeline.core.DEFAULT_CLAUDE_MODEL
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.*
import io.ktor.server.netty.EngineMain
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.koin.ktor.ext.get
import org.koin.ktor.plugin.Koin

fun main(args: Array<String>) {
    EngineMain.main(args)
}

fun Application.module() {
    val config = buildAgentConfig(environment.config)
    val scope = CoroutineScope(Dispatchers.IO)
    install(Koin) { modules(agentModule(config, scope)) }
    val agentLaunchService = get<AgentLaunchService>()
    scope.launch { agentLaunchService.recoverInterruptedJobs() }
    // Resolve Cloud Run client before routing block to avoid Ktor routing DSL name collision
    val cloudRunJobsClient = agentLaunchService.cloudRun?.client
    val jobRegistry = agentLaunchService.cloudRun?.jobs
    val agentService: AgentService = get()
    val jobCompletionNotifier = get<JobCompletionNotifier>()
    configureContentNegotiation()
    configureCallLogging()
    configureStatusPages()
    routing {
        get("/health") { call.respond(HttpStatusCode.OK, "OK") }
        webhookRoutes(config.jiraBotAccountId)
        githubWebhookRoutes(config.githubWebhookSecret, config.githubBotLogin)
        if (config.pubSubWebhookSecret.isNotBlank() && cloudRunJobsClient != null && jobRegistry != null) {
            pubSubWebhookRoutes(
                config.pubSubWebhookSecret, cloudRunJobsClient, jobRegistry,
                PostCompletionActions(agentService, agentLaunchService, jobCompletionNotifier), scope,
            )
        }
    }
}

private fun io.ktor.server.config.ApplicationConfig.str(key: String, default: String = "") =
    propertyOrNull(key)?.getString() ?: default

private fun buildAgentConfig(config: io.ktor.server.config.ApplicationConfig): AgentConfig =
    AgentConfig(
        githubWebhookSecret = config.str("app.github.webhookSecret"),
        githubBotLogin = config.str("app.github.botLogin"),
        jiraEmail = config.str("app.jira.email"),
        jiraApiToken = config.str("app.jira.apiToken"),
        jiraCloudId = config.str("app.jira.cloudId"),
        jiraBotAccountId = config.str("app.jira.botAccountId"),
        jiraBotEmail = config.str("app.jira.botEmail"),
        jiraBotApiToken = config.str("app.jira.botApiToken"),
        gcpProjectId = config.str("app.cloudRun.projectId"),
        gcpRegion = config.str("app.cloudRun.region", "us-central1"),
        gcpJobName = config.str("app.cloudRun.jobName", "media-sage-agent-worker"),
        googleCredentialsJson = decodeBase64(config.str("app.cloudRun.credentialsBase64")),
        supabaseDbUrl = config.str("app.supabase.dbUrl"),
        pubSubWebhookSecret = config.str("app.pubSub.webhookSecret"),
        claudeAuthToken = config.str("app.claude.authToken"),
        claudeBaseUrl = config.str("app.claude.baseUrl", "https://api.anthropic.com"),
        claudeModel = config.str("app.claude.model", DEFAULT_CLAUDE_MODEL),
        githubAppId = config.str("app.githubApp.githubAppId"),
        githubAppPrivateKey = config.str("app.githubApp.githubPrivateKey"),
        githubAppInstallationId = config.str("app.githubApp.githubInstallationId"),
        githubRepoOwner = config.str("app.githubApp.githubRepoOwner"),
        githubRepoName = config.str("app.githubApp.githubRepoName"),
        slackWebhookUrl = config.str("app.slack.webhookUrl"),
    )

private fun decodeBase64(encoded: String): String =
    if (encoded.isNotBlank()) String(java.util.Base64.getDecoder().decode(encoded)) else ""
