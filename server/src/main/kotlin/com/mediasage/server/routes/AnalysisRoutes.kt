package com.mediasage.server.routes

import com.mediasage.server.service.ArticleScraperService
import com.mediasage.server.service.ClaudeApiService
import com.mediasage.server.service.QuoteCandidate
import com.mediasage.server.service.WikimediaService
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.utils.io.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.koin.ktor.ext.inject

// ---- Request/Response DTOs ----

@Serializable
data class EncourageRequest(
    val headlineTitle: String,
    val locale: String = "en",
    val articleUrl: String? = null,
    val recentFigures: List<String> = emptyList()
)

@Serializable
data class MatchRequest(
    val headlineTitle: String,
    val candidates: List<MatchCandidate>
)

@Serializable
data class SseDeltaPayload(val field: String, val text: String)

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
    val scraperService by inject<ArticleScraperService>()
    val wikimediaService by inject<WikimediaService>()

    route("/api/analysis") {
        encourageRoute(claudeService, scraperService, wikimediaService)
        encourageStreamRoute(claudeService, scraperService, wikimediaService)
        @Suppress("DEPRECATION")
        matchRoute(claudeService)
    }
}

private fun Route.encourageRoute(
    claudeService: ClaudeApiService,
    scraperService: ArticleScraperService,
    wikimediaService: WikimediaService
) {
    post("/encourage") {
        val request = call.receive<EncourageRequest>()

        if (request.headlineTitle.isBlank()) {
            call.respond(
                HttpStatusCode.BadRequest,
                mapOf("error" to "headlineTitle is required")
            )
            return@post
        }

        val articleText = request.articleUrl?.let { scraperService.getArticleText(it) }

        val result = claudeService.encourageHeadline(
            headlineTitle = request.headlineTitle,
            locale = request.locale,
            articleText = articleText,
            recentFigures = request.recentFigures
        )

        val figureImageUrl = wikimediaService.getPortraitUrl(result.figureName)

        call.respond(result.copy(figureImageUrl = figureImageUrl))
    }
}

private fun Route.encourageStreamRoute(
    claudeService: ClaudeApiService,
    scraperService: ArticleScraperService,
    wikimediaService: WikimediaService
) {
    post("/encourage/stream") {
        val request = call.receive<EncourageRequest>()
        if (request.headlineTitle.isBlank()) {
            call.respond(HttpStatusCode.BadRequest, mapOf("error" to "headlineTitle is required"))
            return@post
        }
        call.applySseHeaders()
        call.respondBytesWriter(contentType = ContentType.Text.EventStream) {
            val articleText = request.articleUrl?.let { scraperService.getArticleText(it) }
            val figureNameBuffer = StringBuilder()
            claudeService.encourageHeadlineStream(
                headlineTitle = request.headlineTitle,
                locale = request.locale,
                articleText = articleText,
                recentFigures = request.recentFigures
            ).collect { (fieldName, text) ->
                if (fieldName == "FIGURE_NAME") figureNameBuffer.append(text)
                sendSseEvent("delta", sseRouteJson.encodeToString(SseDeltaPayload.serializer(), SseDeltaPayload(fieldName, text)))
            }
            sendSseEvent("done", "")
            wikimediaService.getPortraitUrl(figureNameBuffer.toString())?.let { sendSseEvent("portrait", it) }
        }
    }
}

private fun io.ktor.server.application.ApplicationCall.applySseHeaders() {
    response.headers.append(HttpHeaders.ContentType, "text/event-stream; charset=UTF-8")
    response.headers.append(HttpHeaders.CacheControl, "no-cache, no-transform")
    response.headers.append("X-Accel-Buffering", "no")
}

private suspend fun ByteWriteChannel.sendSseEvent(eventType: String, data: String) {
    writeFully("event: $eventType\ndata: $data\n\n".toByteArray(Charsets.UTF_8))
    flush()
}

private val sseRouteJson = Json { encodeDefaults = true }

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
