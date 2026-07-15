package com.mediasage.appserver.service

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import java.util.UUID

class NewsApiClient(
    private val httpClient: HttpClient,
    private val apiKey: String
) {
    companion object {
        private const val BASE_URL = "https://gnews.io/api/v4"
    }

    suspend fun getTopHeadlines(
        topic: String = "world",
        language: String = "en",
        country: String = "us",
        limit: Int = 10
    ): List<NewsArticle> {
        val response = httpClient.get("$BASE_URL/top-headlines") {
            parameter("token", apiKey)
            parameter("topic", topic)
            parameter("lang", language)
            parameter("country", country)
            parameter("max", limit)
        }

        if (!response.status.isSuccess()) {
            throw NewsApiException(
                statusCode = response.status.value,
                message = "News API error (${response.status}): ${response.bodyAsText()}"
            )
        }

        return response.body<GNewsResponse>().articles
            .distinctBy { it.url }
            .map { it.toNewsArticle() }
    }

    suspend fun searchNews(
        query: String,
        language: String = "en",
        country: String = "us",
        limit: Int = 10
    ): List<NewsArticle> {
        val response = httpClient.get("$BASE_URL/search") {
            parameter("token", apiKey)
            parameter("q", query)
            parameter("lang", language)
            parameter("country", country)
            parameter("max", limit)
        }

        if (!response.status.isSuccess()) {
            throw NewsApiException(
                statusCode = response.status.value,
                message = "News API error (${response.status}): ${response.bodyAsText()}"
            )
        }

        return response.body<GNewsResponse>().articles
            .distinctBy { it.url }
            .map { it.toNewsArticle() }
    }

    private fun GNewsArticle.toNewsArticle() = NewsArticle(
        uuid = UUID.nameUUIDFromBytes(url.toByteArray()).toString(),
        title = title,
        description = description,
        snippet = content.take(200),
        url = url,
        imageUrl = image.orEmpty(),
        publishedAt = publishedAt,
        source = source.name
    )
}

class NewsApiException(
    val statusCode: Int,
    override val message: String
) : RuntimeException(message)
