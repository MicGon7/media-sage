package com.mediasage.server

import com.mediasage.server.db.ServerDatabase
import com.mediasage.server.di.serverModule
import com.mediasage.server.plugins.*
import com.mediasage.server.routes.*
import io.ktor.server.application.*
import io.ktor.server.netty.EngineMain
import io.ktor.server.routing.routing
import org.koin.ktor.plugin.Koin

fun main(args: Array<String>) {
    EngineMain.main(args)
}

/** Application module referenced in application.conf. Installs plugins and routes. */
fun Application.module() {
    val claudeApiKey = environment.config.propertyOrNull("app.claude.apiKey")?.getString() ?: ""
    val newsApiKey = environment.config.propertyOrNull("app.news.apiKey")?.getString() ?: ""
    val scriptureApiKey = environment.config.propertyOrNull("app.scripture.apiKey")?.getString() ?: ""
    val baseUrl = environment.config.propertyOrNull("app.baseUrl")?.getString() ?: "http://localhost:8080"

    install(Koin) {
        modules(serverModule(claudeApiKey, newsApiKey, scriptureApiKey, baseUrl))
    }

    configureContentNegotiation()
    configureCORS()
    configureCallLogging()
    configureStatusPages()
    configureRouting()

    initDatabase()
}

private fun Application.initDatabase() {
    val postgresUrl = environment.config.propertyOrNull("app.supabase.dbUrl")?.getString()
    if (postgresUrl != null) {
        ServerDatabase.init(postgresUrl = postgresUrl)
        return
    }
    val dbPath = environment.config.propertyOrNull("app.db.path")?.getString()
        ?: error("DB_PATH is not set. Export an absolute path: export DB_PATH=<path>")
    ServerDatabase.init(dbPath = dbPath)
}

fun Application.configureRouting() {
    routing {
        healthRoutes()
        newsRoutes()
        analysisRoutes()
        dailyReflectionRoutes()
        scriptureRoutes()
        figureRoutes()
        assignmentDefaultsRoutes()
    }
}
