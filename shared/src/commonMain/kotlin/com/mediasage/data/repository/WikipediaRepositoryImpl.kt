package com.mediasage.data.repository

import com.mediasage.domain.repository.WikipediaRepository
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class WikipediaRepositoryImpl(private val httpClient: HttpClient) : WikipediaRepository {

    companion object {
        private const val API_URL = "https://en.wikipedia.org/w/api.php"
    }

    override suspend fun getBio(figureName: String): String? {
        return try {
            val response = httpClient.get(API_URL) {
                parameter("action", "query")
                parameter("titles", figureName)
                parameter("redirects", "")
                parameter("prop", "extracts")
                parameter("exintro", true)
                parameter("exsentences", 5)
                parameter("explaintext", true)
                parameter("format", "json")
            }
            val json = response.body<JsonObject>()
            val pages = json["query"]?.jsonObject?.get("pages")?.jsonObject ?: return null
            val page = pages.values.firstOrNull()?.jsonObject ?: return null
            page["extract"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() }
        } catch (_: Exception) {
            null
        }
    }
}
