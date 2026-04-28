package com.mediasage.server

import com.mediasage.server.service.WikimediaService
import io.ktor.client.*
import io.ktor.client.engine.mock.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class WikimediaServiceTest {

    private fun createMockClient(responseBody: String): HttpClient {
        return HttpClient(MockEngine { _ ->
            respond(
                content = responseBody,
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }) {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true })
            }
        }
    }

    private val responseWithThumbnail = """
        {
          "query": {
            "pages": {
              "12345": {
                "pageid": 12345,
                "title": "Augustine of Hippo",
                "thumbnail": {
                  "source": "https://upload.wikimedia.org/wikipedia/commons/thumb/a/a4/Augustine.jpg/300px-Augustine.jpg",
                  "width": 300,
                  "height": 400
                }
              }
            }
          }
        }
    """.trimIndent()

    private val responseWithoutThumbnail = """
        {
          "query": {
            "pages": {
              "-1": {
                "ns": 0,
                "title": "Unknown Figure"
              }
            }
          }
        }
    """.trimIndent()

    @Test
    fun getPortraitUrlReturnsUrlWhenThumbnailPresent() = runTest {
        val service = WikimediaService(createMockClient(responseWithThumbnail))

        val url = service.getPortraitUrl("Augustine of Hippo")

        assertEquals(
            "https://upload.wikimedia.org/wikipedia/commons/thumb/a/a4/Augustine.jpg/300px-Augustine.jpg",
            url
        )
    }

    @Test
    fun getPortraitUrlReturnsNullWhenNoThumbnail() = runTest {
        val service = WikimediaService(createMockClient(responseWithoutThumbnail))

        val url = service.getPortraitUrl("Unknown Figure")

        assertNull(url)
    }

    @Test
    fun getPortraitUrlDoesNotThrowOnNullResult() = runTest {
        val service = WikimediaService(createMockClient(responseWithoutThumbnail))

        // Should complete without NullPointerException
        val first = service.getPortraitUrl("Unknown Figure")
        val second = service.getPortraitUrl("Unknown Figure")

        assertNull(first)
        assertNull(second)
    }

    @Test
    fun getPortraitUrlCachesNullResultWithoutCrash() = runTest {
        var callCount = 0
        val client = HttpClient(MockEngine { _ ->
            callCount++
            respond(
                content = responseWithoutThumbnail,
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }) {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true })
            }
        }
        val service = WikimediaService(client)

        service.getPortraitUrl("Unknown Figure")
        service.getPortraitUrl("Unknown Figure")

        // Second call should be served from cache — only one HTTP request made
        assertEquals(1, callCount)
    }

    @Test
    fun getPortraitUrlCachesValidUrl() = runTest {
        var callCount = 0
        val client = HttpClient(MockEngine { _ ->
            callCount++
            respond(
                content = responseWithThumbnail,
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }) {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true })
            }
        }
        val service = WikimediaService(client)

        val first = service.getPortraitUrl("Augustine of Hippo")
        val second = service.getPortraitUrl("Augustine of Hippo")

        assertEquals(first, second)
        assertEquals(1, callCount)
    }
}
