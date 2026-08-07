package com.mediasage.appserver.routes

import com.mediasage.appserver.repository.HeadlineRepository
import com.mediasage.appserver.service.ArticleScraperService
import com.mediasage.appserver.service.NewsApiClient
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.koin.ktor.ext.inject

fun Route.newsRoutes() {
    val newsClient by inject<NewsApiClient>()
    val scraperService by inject<ArticleScraperService>()
    val headlineRepository by inject<HeadlineRepository>()

    route("/api/news") {
        get("/headlines") {
            val category = call.parameters["category"]
            val limit = call.parameters["limit"]?.toIntOrNull() ?: 10

            // Served from the twice-daily cache populated by HeadlineFetchService — no live
            // provider call here, so read volume never increases the number of GNews requests.
            val articles = headlineRepository.getStored(category = category, limit = limit)

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

            val articles = newsClient.searchNews(query = query, language = language, limit = limit)

            scraperService.preScrape(articles.map { it.url })

            call.respond(articles)
        }
    }
}
