package com.mediasage.data.remote

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*

class MediaSageApiImpl(
    private val httpClient: HttpClient,
    private val baseUrl: String
) : MediaSageApi {

    override suspend fun getFigures(since: Long?): FiguresResponse {
        return httpClient.get("$baseUrl/api/figures") {
            if (since != null) parameter("since", since)
        }.body()
    }

    override suspend fun getHeadlines(locale: String, limit: Int): List<NewsArticleDto> {
        return httpClient.get("$baseUrl/api/news/headlines") {
            parameter("locale", locale)
            parameter("limit", limit)
        }.body()
    }

    override suspend fun searchNews(query: String, limit: Int): List<NewsArticleDto> {
        return httpClient.get("$baseUrl/api/news/search") {
            parameter("query", query)
            parameter("limit", limit)
        }.body()
    }

    override suspend fun encourage(request: EncourageRequestDto): EncourageResultDto {
        return httpClient.post("$baseUrl/api/analysis/encourage") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }.body()
    }

    @Suppress("DEPRECATION")
    override suspend fun matchQuote(request: MatchRequestDto): MatchResultDto {
        return httpClient.post("$baseUrl/api/analysis/match") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }.body()
    }

    override suspend fun searchScripture(query: String, limit: Int): List<ScriptureVerseDto> {
        return httpClient.get("$baseUrl/api/scripture/search") {
            parameter("query", query)
            parameter("limit", limit)
        }.body()
    }

    override suspend fun getPassage(passageId: String): ScripturePassageDto {
        return httpClient.get("$baseUrl/api/scripture/passage/$passageId").body()
    }

    override suspend fun getDailyReflection(request: DailyReflectionRequestDto): DailyReflectionResponseDto {
        return httpClient.post("$baseUrl/api/analysis/daily-reflection") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }.body()
    }
}
