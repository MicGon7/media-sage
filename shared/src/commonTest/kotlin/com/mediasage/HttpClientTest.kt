package com.mediasage

import io.ktor.client.*
import io.ktor.client.engine.mock.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class HttpClientTest {

    @Test
    fun mockEngineReturnsConfiguredResponse() = runTest {
        val mockEngine = MockEngine { _ ->
            respond(
                content = """{"status":"ok"}""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }

        val client = HttpClient(mockEngine)
        val response = client.get("https://example.com/api/test")

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("""{"status":"ok"}""", response.bodyAsText())
        client.close()
    }

    @Test
    fun mockEngineHandlesErrorResponse() = runTest {
        val mockEngine = MockEngine { _ ->
            respond(
                content = """{"error":"not found"}""",
                status = HttpStatusCode.NotFound,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }

        val client = HttpClient(mockEngine)
        val response = client.get("https://example.com/api/missing")

        assertEquals(HttpStatusCode.NotFound, response.status)
        client.close()
    }
}
