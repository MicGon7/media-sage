package com.mediasage.appserver.service

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*

class ScriptureApiClient(
    private val httpClient: HttpClient,
    private val apiKey: String
) {
    companion object {
        private const val BASE_URL = "https://api.scripture.api.bible/v1"
        // ASV (American Standard Version) — public domain, good default
        const val DEFAULT_BIBLE_ID = "06125adad2d5898a-01"
    }

    suspend fun searchVerses(
        query: String,
        bibleId: String = DEFAULT_BIBLE_ID,
        limit: Int = 10
    ): List<ScriptureVerse> {
        val response = httpClient.get("$BASE_URL/bibles/$bibleId/search") {
            header("api-key", apiKey)
            parameter("query", query)
            parameter("limit", limit)
        }

        if (!response.status.isSuccess()) {
            throw ScriptureApiException(
                statusCode = response.status.value,
                message = "Scripture API error (${response.status}): ${response.bodyAsText()}"
            )
        }

        return response.body<ScriptureSearchResponse>().data.verses
    }

    suspend fun getPassage(
        passageId: String,
        bibleId: String = DEFAULT_BIBLE_ID
    ): ScripturePassage {
        val response = httpClient.get("$BASE_URL/bibles/$bibleId/passages/$passageId") {
            header("api-key", apiKey)
            parameter("content-type", "text")
            parameter("include-notes", false)
            parameter("include-titles", false)
            parameter("include-chapter-numbers", false)
            parameter("include-verse-numbers", true)
        }

        if (!response.status.isSuccess()) {
            throw ScriptureApiException(
                statusCode = response.status.value,
                message = "Scripture API error (${response.status}): ${response.bodyAsText()}"
            )
        }

        return response.body<ScripturePassageResponse>().data
    }
}

class ScriptureApiException(
    val statusCode: Int,
    override val message: String
) : RuntimeException(message)
