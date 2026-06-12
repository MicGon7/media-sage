package com.mediasage.appserver.routes

import com.mediasage.appserver.db.ServerDatabase
import com.mediasage.appserver.repository.FigureRepository
import com.mediasage.appserver.service.ArticleScraperService
import com.mediasage.appserver.service.ClaudeApiService
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import org.koin.ktor.ext.inject

@Serializable
private data class EncourageRequest(
    val headlineTitle: String,
    val locale: String = "en",
    val articleUrl: String? = null,
    val articleSnippet: String? = null
)

/** Analysis endpoints — Claude AI provides encouragement for headlines. */
fun Route.analysisRoutes() {
    val claudeService by inject<ClaudeApiService>()
    val scraperService by inject<ArticleScraperService>()
    val figureRepository by inject<FigureRepository>()

    route("/api/analysis") {
        encourageRoute(claudeService, scraperService, figureRepository)
    }
}

private fun Route.encourageRoute(
    claudeService: ClaudeApiService,
    scraperService: ArticleScraperService,
    figureRepository: FigureRepository
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

        val articleText = request.articleSnippet
            ?: request.articleUrl?.let { scraperService.getArticleText(it) }

        val candidates = ServerDatabase.fetchQuoteCandidates()
        val result = claudeService.encourageHeadline(
            headlineTitle = request.headlineTitle,
            candidates = candidates,
            locale = request.locale,
            articleText = articleText
        )

        val figureImageUrl = figureRepository.getPortraitUrl(result.figureName)

        call.respond(result.copy(figureImageUrl = figureImageUrl))
    }
}
