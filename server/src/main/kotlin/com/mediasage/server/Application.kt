package com.mediasage.server

import io.ktor.server.application.*
import io.ktor.server.netty.EngineMain
import io.ktor.server.routing.routing
import com.mediasage.server.di.serverModule
import com.mediasage.server.plugins.*
import com.mediasage.server.routes.*
import org.koin.ktor.plugin.Koin

fun main(args: Array<String>) {
    EngineMain.main(args)
}

/** Application module referenced in application.conf. Installs plugins and routes. */
fun Application.module() {
    val claudeApiKey = environment.config
        .propertyOrNull("app.claude.apiKey")?.getString() ?: ""
    val newsApiKey = environment.config
        .propertyOrNull("app.news.apiKey")?.getString() ?: ""
    val scriptureApiKey = environment.config
        .propertyOrNull("app.scripture.apiKey")?.getString() ?: ""
    val agentRepoPath = environment.config
        .propertyOrNull("app.agent.repoPath")?.getString() ?: ""

    install(Koin) {
        modules(serverModule(claudeApiKey, newsApiKey, scriptureApiKey, agentRepoPath))
    }

    configureContentNegotiation()
    configureCORS()
    configureCallLogging()
    configureStatusPages()
    configureRouting()
}

fun Application.configureRouting() {
    routing {
        healthRoutes()
        newsRoutes()
        analysisRoutes()
        scriptureRoutes()
        webhookRoutes()
    }
}
