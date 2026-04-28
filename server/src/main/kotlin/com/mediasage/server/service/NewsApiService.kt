package com.mediasage.server.service

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*

class NewsApiService(
    private val httpClient: HttpClient,
    private val apiKey: String
) {
    companion object {
        private const val BASE_URL = "https://api.thenewsapi.com/v1/news"
        private const val EXCLUDED_CATEGORIES = "sports,entertainment"
    }

    suspend fun getTopHeadlines(
        locale: String = "us",
        language: String = "en",
        limit: Int = 10
    ): List<NewsArticle> {
        val response = httpClient.get("$BASE_URL/top") {
            parameter("api_token", apiKey)
            parameter("locale", locale)
            parameter("language", language)
            parameter("limit", limit)
            parameter("exclude_categories", EXCLUDED_CATEGORIES)
        }

        if (!response.status.isSuccess()) {
            throw NewsApiException(
                statusCode = response.status.value,
                message = "News API error (${response.status}): ${response.bodyAsText()}"
            )
        }

        return response.body<NewsApiResponse>().data.distinctBy { it.url }
    }

    suspend fun searchNews(
        query: String,
        language: String = "en",
        limit: Int = 10
    ): List<NewsArticle> {
        val response = httpClient.get("$BASE_URL/all") {
            parameter("api_token", apiKey)
            parameter("search", query)
            parameter("language", language)
            parameter("limit", limit)
        }

        if (!response.status.isSuccess()) {
            throw NewsApiException(
                statusCode = response.status.value,
                message = "News API error (${response.status}): ${response.bodyAsText()}"
            )
        }

        return response.body<NewsApiResponse>().data.distinctBy { it.url }
    }
}

class NewsApiException(
    val statusCode: Int,
    override val message: String
) : RuntimeException(message)
