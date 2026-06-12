package com.mediasage.appserver

import com.mediasage.appserver.service.ScriptureApiException
import com.mediasage.appserver.service.ScriptureApiService
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

class ScriptureApiServiceTest {

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

    private val searchResponse = """
    {
        "query": "hope",
        "data": {
            "query": "hope",
            "limit": 10,
            "offset": 0,
            "total": 250,
            "verseCount": 2,
            "verses": [
                {
                    "id": "ROM.8.24",
                    "orgId": "ROM.8.24",
                    "bibleId": "06125adad2d5898a-01",
                    "bookId": "ROM",
                    "chapterId": "ROM.8",
                    "reference": "Romans 8:24",
                    "text": "For in hope we were saved."
                },
                {
                    "id": "HEB.11.1",
                    "orgId": "HEB.11.1",
                    "bibleId": "06125adad2d5898a-01",
                    "bookId": "HEB",
                    "chapterId": "HEB.11",
                    "reference": "Hebrews 11:1",
                    "text": "Now faith is assurance of things hoped for, proof of things not seen."
                }
            ]
        }
    }
    """.trimIndent()

    private val passageResponse = """
    {
        "data": {
            "id": "JHN.3.16",
            "orgId": "JHN.3.16",
            "bibleId": "06125adad2d5898a-01",
            "reference": "John 3:16",
            "content": "For God so loved the world, that he gave his only begotten Son, that whosoever believeth on him should not perish, but have eternal life.",
            "copyright": "PUBLIC DOMAIN"
        }
    }
    """.trimIndent()

    @Test
    fun searchVersesReturnsResults() = runTest {
        val client = createMockClient(searchResponse)
        val service = ScriptureApiService(client, "test-api-key")

        val verses = service.searchVerses("hope")

        assertEquals(2, verses.size)
        assertEquals("Romans 8:24", verses[0].reference)
        assertEquals("Hebrews 11:1", verses[1].reference)
        assertTrue(verses[0].text.contains("hope"))
    }

    @Test
    fun getPassageReturnsContent() = runTest {
        val client = createMockClient(passageResponse)
        val service = ScriptureApiService(client, "test-api-key")

        val passage = service.getPassage("JHN.3.16")

        assertEquals("John 3:16", passage.reference)
        assertTrue(passage.content.contains("God so loved"))
    }

    @Test
    fun searchVersesThrowsOnApiError() = runTest {
        val client = createMockClient("""{"error":"Unauthorized"}""", HttpStatusCode.Unauthorized)
        val service = ScriptureApiService(client, "bad-key")

        val exception = assertFailsWith<ScriptureApiException> {
            service.searchVerses("hope")
        }

        assertEquals(401, exception.statusCode)
    }

    @Test
    fun verseFieldsMappedCorrectly() = runTest {
        val client = createMockClient(searchResponse)
        val service = ScriptureApiService(client, "test-api-key")

        val verse = service.searchVerses("hope").first()

        assertEquals("ROM.8.24", verse.id)
        assertEquals("ROM", verse.bookId)
        assertEquals("ROM.8", verse.chapterId)
    }
}
