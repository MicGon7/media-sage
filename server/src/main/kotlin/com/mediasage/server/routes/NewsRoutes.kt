package com.mediasage.server.routes

import com.mediasage.server.service.ArticleScraperService
import com.mediasage.server.service.NewsApiService
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.koin.ktor.ext.inject

/** News headline endpoints — fetches from TheNewsAPI. */
fun Route.newsRoutes() {
    val newsService by inject<NewsApiService>()
    val scraperService by inject<ArticleScraperService>()

    route("/api/news") {
        get("/headlines") {
            val locale = call.parameters["locale"] ?: "us"
            val language = call.parameters["language"] ?: "en"
            val limit = call.parameters["limit"]?.toIntOrNull() ?: 10

            val articles = newsService.getTopHeadlines(locale, language, limit)

            // Pre-scrape articles in the background so text is ready when user taps
            scraperService.preScrape(articles.map { it.url })

            call.respond(articles)
        }

        get("/search") {
            val query = call.parameters["query"]
                ?: return@get call.respond(
                    io.ktor.http.HttpStatusCode.BadRequest,
                    mapOf("error" to "query parameter is required")
                )
            val language = call.parameters["language"] ?: "en"
            val limit = call.parameters["limit"]?.toIntOrNull() ?: 10

            val articles = newsService.searchNews(query, language, limit)

            scraperService.preScrape(articles.map { it.url })

            call.respond(articles)
        }
    }
}
