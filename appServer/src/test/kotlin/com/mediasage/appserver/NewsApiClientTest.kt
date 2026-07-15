package com.mediasage.appserver

import com.mediasage.appserver.service.NewsApiException
import com.mediasage.appserver.service.NewsApiClient
import io.ktor.client.*
import io.ktor.client.engine.mock.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull

class NewsApiClientTest {

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
        "totalArticles": 2,
        "articles": [
            {
                "title": "Global markets rally on trade deal hopes",
                "description": "Markets surged today...",
                "content": "Markets surged today after trade talks...",
                "url": "https://example.com/markets",
                "image": "https://example.com/img.jpg",
                "publishedAt": "2026-04-19T10:00:00Z",
                "source": { "name": "Reuters", "url": "https://reuters.com" }
            },
            {
                "title": "Earthquake strikes Pacific region",
                "description": "A 6.5 magnitude earthquake...",
                "content": "A 6.5 magnitude earthquake struck the Pacific...",
                "url": "https://example.com/earthquake",
                "image": null,
                "publishedAt": "2026-04-19T09:00:00Z",
                "source": { "name": "AP News", "url": "https://apnews.com" }
            }
        ]
    }
    """.trimIndent()

    @Test
    fun getTopHeadlinesReturnsArticles() = runTest {
        val client = NewsApiClient(createMockClient(sampleResponse), "test-api-key")

        val articles = client.getTopHeadlines()

        assertEquals(2, articles.size)
        assertEquals("Global markets rally on trade deal hopes", articles[0].title)
        assertEquals("Reuters", articles[0].source)
        assertEquals("AP News", articles[1].source)
    }

    @Test
    fun searchNewsReturnsArticles() = runTest {
        val client = NewsApiClient(createMockClient(sampleResponse), "test-api-key")

        val articles = client.searchNews("earthquake")

        assertEquals(2, articles.size)
    }

    @Test
    fun getTopHeadlinesThrowsOnApiError() = runTest {
        val httpClient = createMockClient("""{"message":"Invalid API token"}""", HttpStatusCode.Unauthorized)
        val client = NewsApiClient(httpClient, "bad-key")

        val exception = assertFailsWith<NewsApiException> {
            client.getTopHeadlines()
        }

        assertEquals(401, exception.statusCode)
    }

    private val duplicateUrlResponse = """
    {
        "totalArticles": 3,
        "articles": [
            {
                "title": "Global markets rally",
                "description": "",
                "content": "",
                "url": "https://example.com/markets",
                "image": null,
                "publishedAt": "2026-04-19T10:00:00Z",
                "source": { "name": "Reuters", "url": "https://reuters.com" }
            },
            {
                "title": "Global markets rally",
                "description": "",
                "content": "",
                "url": "https://example.com/markets",
                "image": null,
                "publishedAt": "2026-04-19T10:00:00Z",
                "source": { "name": "Reuters", "url": "https://reuters.com" }
            },
            {
                "title": "Earthquake strikes Pacific region",
                "description": "",
                "content": "",
                "url": "https://example.com/earthquake",
                "image": null,
                "publishedAt": "2026-04-19T09:00:00Z",
                "source": { "name": "AP News", "url": "https://apnews.com" }
            }
        ]
    }
    """.trimIndent()

    @Test
    fun getTopHeadlinesDeduplicatesByUrl() = runTest {
        val client = NewsApiClient(createMockClient(duplicateUrlResponse), "test-api-key")

        val articles = client.getTopHeadlines()

        assertEquals(2, articles.size)
        assertEquals("https://example.com/markets", articles[0].url)
        assertEquals("https://example.com/earthquake", articles[1].url)
    }

    private val duplicateSearchResponse = """
    {
        "totalArticles": 2,
        "articles": [
            {
                "title": "Climate summit reaches agreement",
                "description": "",
                "content": "",
                "url": "https://example.com/climate",
                "image": null,
                "publishedAt": "2026-04-19T08:00:00Z",
                "source": { "name": "BBC", "url": "https://bbc.com" }
            },
            {
                "title": "Climate summit reaches agreement",
                "description": "",
                "content": "",
                "url": "https://example.com/climate",
                "image": null,
                "publishedAt": "2026-04-19T08:00:00Z",
                "source": { "name": "BBC", "url": "https://bbc.com" }
            }
        ]
    }
    """.trimIndent()

    @Test
    fun searchNewsDeduplicatesByUrl() = runTest {
        val client = NewsApiClient(createMockClient(duplicateSearchResponse), "test-api-key")

        val articles = client.searchNews("climate")

        assertEquals(1, articles.size)
        assertEquals("https://example.com/climate", articles[0].url)
    }

    @Test
    fun articleFieldsMappedCorrectly() = runTest {
        val client = NewsApiClient(createMockClient(sampleResponse), "test-api-key")

        val article = client.getTopHeadlines().first()

        assertEquals("https://example.com/markets", article.url)
        assertEquals("https://example.com/img.jpg", article.imageUrl)
        assertEquals("2026-04-19T10:00:00Z", article.publishedAt)
        assertEquals("Reuters", article.source)
        assertNotNull(article.uuid)
        assertEquals(UUID.nameUUIDFromBytes("https://example.com/markets".toByteArray()).toString(), article.uuid)
    }

    @Test
    fun getTopHeadlinesPassesTopicParam() = runTest {
        val client = NewsApiClient(createMockClient(sampleResponse), "test-api-key")

        val articles = client.getTopHeadlines(topic = "world")

        assertEquals(2, articles.size)
    }
}
