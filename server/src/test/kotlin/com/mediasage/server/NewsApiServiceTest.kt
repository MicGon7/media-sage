package com.mediasage.server

import com.mediasage.server.service.NewsApiException
import com.mediasage.server.service.NewsApiService
import io.ktor.client.*
import io.ktor.client.engine.mock.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class NewsApiServiceTest {

    private fun createMockClient(responseBody: String, status: HttpStatusCode = HttpStatusCode.OK): HttpClient {
        return HttpClient(MockEngine { _ ->
            respond(
                content = responseBody,
                status = status,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }) {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true })
            }
        }
    }

    private val sampleResponse = """
    {
        "meta": { "found": 2, "returned": 2, "limit": 10, "page": 1 },
        "data": [
            {
                "uuid": "abc-123",
                "title": "Global markets rally on trade deal hopes",
                "description": "Markets surged today...",
                "keywords": "markets,trade,economy",
                "snippet": "Markets surged today after...",
                "url": "https://example.com/markets",
                "image_url": "https://example.com/img.jpg",
                "language": "en",
                "published_at": "2026-04-19T10:00:00.000000Z",
                "source": "Reuters",
                "categories": ["business"],
                "locale": "us"
            },
            {
                "uuid": "def-456",
                "title": "Earthquake strikes Pacific region",
                "description": "A 6.5 magnitude earthquake...",
                "keywords": "earthquake,pacific",
                "snippet": "A 6.5 magnitude earthquake struck...",
                "url": "https://example.com/earthquake",
                "image_url": "",
                "language": "en",
                "published_at": "2026-04-19T09:00:00.000000Z",
                "source": "AP News",
                "categories": ["general"],
                "locale": "us"
            }
        ]
    }
    """.trimIndent()

    @Test
    fun getTopHeadlinesReturnsArticles() = runTest {
        val client = createMockClient(sampleResponse)
        val service = NewsApiService(client, "test-api-key")

        val articles = service.getTopHeadlines()

        assertEquals(2, articles.size)
        assertEquals("Global markets rally on trade deal hopes", articles[0].title)
        assertEquals("Reuters", articles[0].source)
        assertEquals("AP News", articles[1].source)
    }

    @Test
    fun searchNewsReturnsArticles() = runTest {
        val client = createMockClient(sampleResponse)
        val service = NewsApiService(client, "test-api-key")

        val articles = service.searchNews("earthquake")

        assertEquals(2, articles.size)
    }

    @Test
    fun getTopHeadlinesThrowsOnApiError() = runTest {
        val client = createMockClient("""{"message":"Invalid API token"}""", HttpStatusCode.Unauthorized)
        val service = NewsApiService(client, "bad-key")

        val exception = assertFailsWith<NewsApiException> {
            service.getTopHeadlines()
        }

        assertEquals(401, exception.statusCode)
    }

    @Test
    fun articleFieldsMappedCorrectly() = runTest {
        val client = createMockClient(sampleResponse)
        val service = NewsApiService(client, "test-api-key")

        val article = service.getTopHeadlines().first()

        assertEquals("abc-123", article.uuid)
        assertEquals("https://example.com/img.jpg", article.imageUrl)
        assertEquals("2026-04-19T10:00:00.000000Z", article.publishedAt)
        assertTrue(article.categories.contains("business"))
    }
}
