package com.mediasage.server

import com.mediasage.server.di.JiraConfig
import com.mediasage.server.di.serverModule
import com.mediasage.server.plugins.*
import com.mediasage.server.routes.*
import io.ktor.server.application.*
import io.ktor.server.netty.EngineMain
import io.ktor.server.routing.routing
import org.koin.ktor.plugin.Koin
import org.slf4j.LoggerFactory

private val startupLog = LoggerFactory.getLogger("com.mediasage.server.Startup")

fun main(args: Array<String>) {
    EngineMain.main(args)
}

/** Masks an API key for safe logging: shows the first 4 chars and replaces the rest with asterisks. */
private fun String.masked(): String = if (length <= 4) "****" else take(4) + "*".repeat(length - 4)

/**
 * Validates that a required API key is non-empty. Logs a masked preview when present,
 * or a clear WARNING when missing so Railway log tails immediately surface the problem.
 */
private fun validateApiKey(name: String, value: String) {
    if (value.isEmpty()) {
        startupLog.warn("MISSING API KEY: {} is empty — set the {} environment variable", name, name.uppercase().replace('.', '_'))
    } else {
        startupLog.info("API key loaded: {} = {}", name, value.masked())
    }
}

/** Application module referenced in application.conf. Installs plugins and routes. */
fun Application.module() {
    val claudeApiKey = environment.config.propertyOrNull("app.claude.apiKey")?.getString() ?: ""
    val newsApiKey = environment.config.propertyOrNull("app.news.apiKey")?.getString() ?: ""
    val scriptureApiKey = environment.config.propertyOrNull("app.scripture.apiKey")?.getString() ?: ""
    val agentRepoPath = environment.config.propertyOrNull("app.agent.repoPath")?.getString() ?: ""
    val jiraConfig = JiraConfig(
        email = environment.config.propertyOrNull("app.jira.email")?.getString() ?: "",
        apiToken = environment.config.propertyOrNull("app.jira.apiToken")?.getString() ?: "",
        cloudId = environment.config.propertyOrNull("app.jira.cloudId")?.getString() ?: ""
    )

    // ── Startup key validation ────────────────────────────────────────────────
    // Logs a masked preview of each key so Railway log tails can confirm the
    // environment variables are actually reaching the container. A WARN line
    // for any empty key makes the root cause of 401 errors immediately obvious.
    startupLog.info("=== API key startup check ===")
    validateApiKey("CLAUDE_API_KEY", claudeApiKey)
    validateApiKey("NEWS_API_KEY", newsApiKey)
    validateApiKey("SCRIPTURE_API_KEY", scriptureApiKey)
    if (agentRepoPath.isEmpty()) {
        startupLog.warn("MISSING CONFIG: AGENT_REPO_PATH is empty — agent launch will be unavailable")
    } else {
        startupLog.info("Agent repo path: {}", agentRepoPath)
    }
    startupLog.info("=== End API key startup check ===")

    install(Koin) {
        modules(serverModule(claudeApiKey, newsApiKey, scriptureApiKey, agentRepoPath, jiraConfig, this@module))
    }

    configureContentNegotiation()
    configureCORS()
    configureCallLogging()
    configureStatusPages()
    configureRouting()
}

fun Application.configureRouting() {
    val githubWebhookSecret = environment.config.propertyOrNull("app.github.webhookSecret")?.getString() ?: ""

    routing {
        healthRoutes()
        newsRoutes()
        analysisRoutes()
        scriptureRoutes()
        webhookRoutes()
        githubWebhookRoutes(githubWebhookSecret)
    }
}
