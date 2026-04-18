package com.mediasage.server

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import kotlin.test.Test
import kotlin.test.assertEquals

class ApplicationTest {

    @Test
    fun healthEndpointReturnsOk() = testApplication {
        application { module() }

        val response = client.get("/health")
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("OK", response.bodyAsText())
    }

    @Test
    fun unknownRouteReturns404() = testApplication {
        application { module() }

        val response = client.get("/nonexistent")
        assertEquals(HttpStatusCode.NotFound, response.status)
    }
}
