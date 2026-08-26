package com.mediasage.appserver.routes

import com.mediasage.appserver.db.ServerDatabase
import com.mediasage.appserver.repository.ClaudeCallLimitRepository
import com.mediasage.appserver.repository.EncouragementCacheRepository
import com.mediasage.appserver.repository.FigureRepository
import com.mediasage.appserver.service.ArticleScraperService
import com.mediasage.appserver.service.ClaudeApiClient
import com.mediasage.appserver.service.DailyLimitExceededException
import com.mediasage.appserver.service.EncourageResult
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import org.koin.core.qualifier.named
import org.koin.ktor.ext.inject
import java.time.LocalDate
import java.time.ZoneOffset

@Serializable
private data class EncourageRequest(
    val headlineTitle: String,
    val locale: String = "en",
    val articleUrl: String? = null,
    val articleSnippet: String? = null
)

/** Analysis endpoints — Claude AI provides encouragement for headlines. */
fun Route.analysisRoutes() {
    val claudeClient by inject<ClaudeApiClient>()
    val scraperService by inject<ArticleScraperService>()
    val figureRepository by inject<FigureRepository>()
    val encouragementCacheRepository by inject<EncouragementCacheRepository>()
    val claudeCallLimitRepository by inject<ClaudeCallLimitRepository>()
    val dailyClaudeCallLimit by inject<Int>(named("dailyClaudeCallLimit"))

    route("/api/analysis") {
        encourageRoute(
            claudeClient,
            scraperService,
            figureRepository,
            encouragementCacheRepository,
            claudeCallLimitRepository,
            dailyClaudeCallLimit
        )
    }
}

private fun Route.encourageRoute(
    claudeClient: ClaudeApiClient,
    scraperService: ArticleScraperService,
    figureRepository: FigureRepository,
    encouragementCacheRepository: EncouragementCacheRepository,
    claudeCallLimitRepository: ClaudeCallLimitRepository,
    dailyClaudeCallLimit: Int
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

        val cached = request.articleUrl?.let { encouragementCacheRepository.getByArticleUrl(it) }
        if (cached != null) {
            val figureImageUrl = figureRepository.getPortraitUrl(cached.figureName)
            call.respond(cached.copy(figureImageUrl = figureImageUrl))
            return@post
        }

        val callDate = LocalDate.now(ZoneOffset.UTC).toString()
        if (!claudeCallLimitRepository.tryConsumeCall(callDate, dailyClaudeCallLimit)) {
            throw DailyLimitExceededException()
        }

        val result = generateEncouragement(request, claudeClient, scraperService)
        request.articleUrl?.let { encouragementCacheRepository.insert(it, result, System.currentTimeMillis()) }

        val figureImageUrl = figureRepository.getPortraitUrl(result.figureName)
        call.respond(result.copy(figureImageUrl = figureImageUrl))
    }
}

private suspend fun generateEncouragement(
    request: EncourageRequest,
    claudeClient: ClaudeApiClient,
    scraperService: ArticleScraperService
): EncourageResult {
    val articleText = request.articleSnippet
        ?: request.articleUrl?.let { scraperService.getArticleText(it) }

    val candidates = ServerDatabase.fetchQuoteCandidates()
    return claudeClient.encourageHeadline(
        headlineTitle = request.headlineTitle,
        candidates = candidates,
        locale = request.locale,
        articleText = articleText
    )
}
