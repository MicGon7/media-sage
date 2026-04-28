package com.mediasage.server.routes

import io.ktor.http.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

/** Health check endpoint — proves the server is alive. Used by CI and monitoring. */
fun Route.healthRoutes() {
    get("/health") {
        call.respondText("OK")
    }

    // ── Debug config endpoint (non-production only) ───────────────────────────
    // Shows which environment variables are loaded and whether each API key is
    // present (never the raw value). Disabled when RAILWAY_ENVIRONMENT=production
    // or when DEBUG_CONFIG_ENABLED is not explicitly set to "true".
    val railwayEnv = System.getenv("RAILWAY_ENVIRONMENT") ?: ""
    val debugEnabled = System.getenv("DEBUG_CONFIG_ENABLED")?.lowercase() == "true"
    val isProduction = railwayEnv.lowercase() == "production"

    if (debugEnabled && !isProduction) {
        get("/debug/config") {
            fun String?.status() = when {
                this == null || this.isEmpty() -> "MISSING"
                else -> "SET (${this.take(4)}${"*".repeat(this.length - 4)})"
            }

            val config = mapOf(
                "RAILWAY_ENVIRONMENT" to (railwayEnv.ifEmpty { "(not set)" }),
                "CLAUDE_API_KEY" to System.getenv("CLAUDE_API_KEY").status(),
                "NEWS_API_KEY" to System.getenv("NEWS_API_KEY").status(),
                "SCRIPTURE_API_KEY" to System.getenv("SCRIPTURE_API_KEY").status(),
                "AGENT_REPO_PATH" to (System.getenv("AGENT_REPO_PATH") ?: "(not set)"),
                "PORT" to (System.getenv("PORT") ?: "(not set)")
            )

            call.respond(HttpStatusCode.OK, config)
        }
    }
}
