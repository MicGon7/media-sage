package com.mediasage.data.remote

import io.ktor.client.*
import io.ktor.client.engine.mock.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MediaSageApiTest {

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

    @Test
    fun getHeadlinesReturnsArticles() = runTest {
        val response = """
        [
            {
                "uuid": "abc-123",
                "title": "Test headline",
                "url": "https://example.com",
                "image_url": "https://example.com/img.jpg",
                "published_at": "2026-04-19T10:00:00Z",
                "source": "Reuters"
            }
        ]
        """.trimIndent()

        val api = MediaSageApiImpl(createMockClient(response), "http://localhost:8080")
        val articles = api.getHeadlines()

        assertEquals(1, articles.size)
        assertEquals("Test headline", articles[0].title)
        assertEquals("Reuters", articles[0].source)
    }

    @Test
    fun matchQuoteReturnsResult() = runTest {
        val response = """
        {
            "selectedQuoteId": 2,
            "confidence": 0.92,
            "explanation": "This quote speaks to the theme of hope.",
            "connectionThemes": ["hope", "suffering"]
        }
        """.trimIndent()

        val api = MediaSageApiImpl(createMockClient(response), "http://localhost:8080")
        val result = api.matchQuote(
            MatchRequestDto(
                headlineTitle = "Test headline",
                candidates = listOf(
                    MatchCandidateDto(id = 1, figureName = "Augustine", text = "Quote 1", source = "Confessions"),
                    MatchCandidateDto(id = 2, figureName = "Corrie ten Boom", text = "Quote 2", source = "The Hiding Place")
                )
            )
        )

        assertEquals(2, result.selectedQuoteId)
        assertEquals(0.92f, result.confidence)
        assertEquals(listOf("hope", "suffering"), result.connectionThemes)
    }

    @Test
    fun searchScriptureReturnsVerses() = runTest {
        val response = """
        [
            {
                "id": "ROM.8.24",
                "bookId": "ROM",
                "chapterId": "ROM.8",
                "reference": "Romans 8:24",
                "text": "For in hope we were saved."
            }
        ]
        """.trimIndent()

        val api = MediaSageApiImpl(createMockClient(response), "http://localhost:8080")
        val verses = api.searchScripture("hope")

        assertEquals(1, verses.size)
        assertEquals("Romans 8:24", verses[0].reference)
        assertTrue(verses[0].text.contains("hope"))
    }

    @Test
    fun getPassageReturnsContent() = runTest {
        val response = """
        {
            "id": "JHN.3.16",
            "reference": "John 3:16",
            "content": "For God so loved the world..."
        }
        """.trimIndent()

        val api = MediaSageApiImpl(createMockClient(response), "http://localhost:8080")
        val passage = api.getPassage("JHN.3.16")

        assertEquals("John 3:16", passage.reference)
        assertTrue(passage.content.contains("God so loved"))
    }
}
