package com.mediasage.appserver.routes

import com.mediasage.appserver.service.ArticleScraperService
import com.mediasage.appserver.service.NewsApiClient
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.koin.ktor.ext.inject

fun Route.newsRoutes() {
    val newsService by inject<NewsApiClient>()
    val scraperService by inject<ArticleScraperService>()

    route("/api/news") {
        get("/headlines") {
            val topic = call.parameters["topic"] ?: "world"
            val language = call.parameters["language"] ?: "en"
            val limit = call.parameters["limit"]?.toIntOrNull() ?: 10

            val articles = newsService.getTopHeadlines(topic = topic, language = language, limit = limit)

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

            val articles = newsService.searchNews(query = query, language = language, limit = limit)

            scraperService.preScrape(articles.map { it.url })

            call.respond(articles)
        }
    }
}
