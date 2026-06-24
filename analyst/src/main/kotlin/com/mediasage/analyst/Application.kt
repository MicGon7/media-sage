package com.mediasage.analyst

import com.mediasage.analyst.di.AnalystConfig
import com.mediasage.analyst.di.analystModule
import com.mediasage.analyst.plugins.configureCallLogging
import com.mediasage.analyst.plugins.configureContentNegotiation
import com.mediasage.analyst.plugins.configureStatusPages
import com.mediasage.analyst.routes.pubSubCompletionRoutes
import com.mediasage.analyst.routes.statsRoutes
import com.mediasage.analyst.scoring.DecisionScorer
import com.mediasage.analyst.stats.PipelineStatsReader
import com.mediasage.pipeline.core.JobRegistry
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.*
import io.ktor.server.netty.EngineMain
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import org.koin.ktor.ext.get
import org.koin.ktor.plugin.Koin

fun main(args: Array<String>) {
    EngineMain.main(args)
}

fun Application.module() {
    val config = buildAnalystConfig(environment.config)
    install(Koin) { modules(analystModule(config)) }
    val statsReader = get<PipelineStatsReader>()
    val jobRegistry = get<JobRegistry>()
    val decisionScorer = get<DecisionScorer>()
    configureContentNegotiation()
    configureCallLogging()
    configureStatusPages()
    routing {
        get("/health") { call.respond(HttpStatusCode.OK, "OK") }
        statsRoutes(statsReader)
        if (config.pubSubWebhookSecret.isNotBlank()) {
            pubSubCompletionRoutes(config.pubSubWebhookSecret, jobRegistry, decisionScorer)
        }
    }
}

private fun buildAnalystConfig(config: io.ktor.server.config.ApplicationConfig): AnalystConfig {
    fun str(key: String) = config.propertyOrNull(key)?.getString() ?: ""
    return AnalystConfig(
        supabaseDbUrl = str("app.supabase.dbUrl"),
        pubSubWebhookSecret = str("app.pubSub.webhookSecret"),
        claudeApiKey = str("app.claude.apiKey"),
    )
}
