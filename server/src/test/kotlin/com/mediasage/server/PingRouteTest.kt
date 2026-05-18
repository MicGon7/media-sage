package com.mediasage.server

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.config.*
import io.ktor.server.testing.*
import kotlin.test.Test
import kotlin.test.assertEquals

class PingRouteTest {

    @Test
    fun pingReturnsPong() = testApplication {
        environment { config = MapApplicationConfig("app.db.path" to ":memory:") }
        application { module() }

        val response = client.get("/ping")
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("pong", response.bodyAsText())
    }
}
