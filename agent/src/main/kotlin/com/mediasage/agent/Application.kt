package com.mediasage.agent

import com.mediasage.agent.di.AgentConfig
import com.mediasage.agent.di.agentModule
import com.mediasage.agent.plugins.*
import com.mediasage.agent.routes.githubWebhookRoutes
import com.mediasage.agent.routes.webhookRoutes
import com.mediasage.agent.service.AgentLaunchService
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
    scope.launch { get<AgentLaunchService>().recoverInterruptedJobs() }
    configureContentNegotiation()
    configureCallLogging()
    configureStatusPages()
    routing {
        get("/health") { call.respond(HttpStatusCode.OK, "OK") }
        webhookRoutes(config.jiraBotAccountId)
        githubWebhookRoutes(config.githubWebhookSecret)
    }
}

private fun buildAgentConfig(config: io.ktor.server.config.ApplicationConfig): AgentConfig {
    fun str(key: String) = config.propertyOrNull(key)?.getString() ?: ""
    fun bool(key: String) = config.propertyOrNull(key)?.getString()?.toBoolean() ?: false
    val credentialsBase64 = str("app.cloudRun.credentialsBase64")
    val credentialsJson = if (credentialsBase64.isNotBlank()) {
        String(java.util.Base64.getDecoder().decode(credentialsBase64))
    } else ""
    return AgentConfig(
        repoPath = str("app.agent.repoPath"),
        githubWebhookSecret = str("app.github.webhookSecret"),
        jiraEmail = str("app.jira.email"),
        jiraApiToken = str("app.jira.apiToken"),
        jiraCloudId = str("app.jira.cloudId"),
        jiraBotAccountId = str("app.jira.botAccountId"),
        jiraBotEmail = str("app.jira.botEmail"),
        jiraBotApiToken = str("app.jira.botApiToken"),
        verboseLogging = bool("app.agent.verboseLogging"),
        useCloudRunWorkers = bool("app.cloudRun.useWorkers"),
        gcpProjectId = str("app.cloudRun.projectId"),
        gcpRegion = config.propertyOrNull("app.cloudRun.region")?.getString() ?: "us-central1",
        gcpJobName = config.propertyOrNull("app.cloudRun.jobName")?.getString() ?: "media-sage-agent-worker",
        googleCredentialsJson = credentialsJson,
        supabaseDbUrl = str("app.supabase.dbUrl"),
        agentBriefingEnabled = bool("app.agent.briefingEnabled")
    )
}
