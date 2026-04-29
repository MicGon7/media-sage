package com.mediasage.server.service

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.util.concurrent.ConcurrentHashMap

class WikimediaService(private val httpClient: HttpClient) {

    companion object {
        private const val API_URL = "https://en.wikipedia.org/w/api.php"
        private const val THUMB_SIZE = 300
    }

    // ConcurrentHashMap forbids null values; "" is the sentinel for "no portrait found"
    private val cache = ConcurrentHashMap<String, String>()

    suspend fun getPortraitUrl(figureName: String): String? {
        val cached = cache[figureName]
        if (cached != null) return if (cached == "") null else cached
        val url = fetchPortraitUrl(figureName)
        cache[figureName] = url ?: ""
        return url
    }

    private suspend fun fetchPortraitUrl(figureName: String): String? {
        return try {
            val response = httpClient.get(API_URL) {
                parameter("action", "query")
                parameter("titles", figureName)
                parameter("redirects", "")
                parameter("prop", "pageimages")
                parameter("pithumbsize", THUMB_SIZE)
                parameter("format", "json")
            }
            val json = response.body<JsonObject>()
            val pages = json["query"]?.jsonObject?.get("pages")?.jsonObject ?: return null
            val page = pages.values.firstOrNull()?.jsonObject ?: return null
            page["thumbnail"]?.jsonObject?.get("source")?.jsonPrimitive?.content
        } catch (_: Exception) {
            null
        }
    }
}
