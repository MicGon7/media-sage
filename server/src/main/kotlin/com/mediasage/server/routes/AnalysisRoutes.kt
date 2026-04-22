package com.mediasage.server.routes

import com.mediasage.server.service.ClaudeApiService
import com.mediasage.server.service.QuoteCandidate
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import org.koin.ktor.ext.inject

// ---- Request/Response DTOs ----

@Serializable
data class EncourageRequest(
    val headlineTitle: String,
    val locale: String = "en",
    val articleText: String? = null
)

@Serializable
data class MatchRequest(
    val headlineTitle: String,
    val candidates: List<MatchCandidate>
)

@Serializable
data class MatchCandidate(
    val id: Long,
    val figureName: String,
    val text: String,
    val source: String,
    val themes: List<String> = emptyList()
)

/** Analysis endpoints — Claude AI provides encouragement for headlines. */
fun Route.analysisRoutes() {
    val claudeService by inject<ClaudeApiService>()

    route("/api/analysis") {
        encourageRoute(claudeService)
        @Suppress("DEPRECATION")
        matchRoute(claudeService)
    }
}

private fun Route.encourageRoute(claudeService: ClaudeApiService) {
    post("/encourage") {
        val request = call.receive<EncourageRequest>()

        if (request.headlineTitle.isBlank()) {
            call.respond(
                HttpStatusCode.BadRequest,
                mapOf("error" to "headlineTitle is required")
            )
            return@post
        }

        val result = claudeService.encourageHeadline(
            headlineTitle = request.headlineTitle,
            locale = request.locale,
            articleText = request.articleText
        )

        call.respond(result)
    }
}

@Deprecated("Use encourageRoute instead — TODO MS-46")
private fun Route.matchRoute(claudeService: ClaudeApiService) {
    post("/match") {
        val request = call.receive<MatchRequest>()

        if (request.candidates.isEmpty()) {
            call.respond(
                HttpStatusCode.BadRequest,
                mapOf("error" to "At least one candidate quote is required")
            )
            return@post
        }

        val result = claudeService.matchQuoteToHeadline(
            headlineTitle = request.headlineTitle,
            candidateQuotes = request.candidates.map { candidate ->
                QuoteCandidate(
                    id = candidate.id,
                    figureName = candidate.figureName,
                    text = candidate.text,
                    source = candidate.source,
                    themes = candidate.themes
                )
            }
        )

        call.respond(result)
    }
}
