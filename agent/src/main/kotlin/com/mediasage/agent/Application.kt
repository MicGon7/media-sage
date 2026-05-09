package com.mediasage.agent

import com.mediasage.agent.di.AgentConfig
import com.mediasage.agent.di.agentModule
import com.mediasage.agent.plugins.*
import com.mediasage.agent.routes.githubWebhookRoutes
import com.mediasage.agent.routes.webhookRoutes
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.*
import io.ktor.server.netty.EngineMain
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.koin.ktor.plugin.Koin

fun main(args: Array<String>) {
    EngineMain.main(args)
}

fun Application.module() {
    val config = AgentConfig(
        repoPath = environment.config.propertyOrNull("app.agent.repoPath")?.getString() ?: "",
        githubWebhookSecret = environment.config.propertyOrNull("app.github.webhookSecret")?.getString() ?: "",
        jiraEmail = environment.config.propertyOrNull("app.jira.email")?.getString() ?: "",
        jiraApiToken = environment.config.propertyOrNull("app.jira.apiToken")?.getString() ?: "",
        jiraCloudId = environment.config.propertyOrNull("app.jira.cloudId")?.getString() ?: "",
        jiraBotAccountId = environment.config.propertyOrNull("app.jira.botAccountId")?.getString() ?: "",
        verboseLogging = environment.config.propertyOrNull("app.agent.verboseLogging")
            ?.getString()?.toBoolean() ?: false
    )

    install(Koin) {
        modules(agentModule(config, CoroutineScope(Dispatchers.IO)))
    }

    configureContentNegotiation()
    configureCallLogging()
    configureStatusPages()

    routing {
        get("/health") { call.respond(HttpStatusCode.OK, "OK") }
        webhookRoutes(config.jiraBotAccountId)
        githubWebhookRoutes(config.githubWebhookSecret)
    }
}
