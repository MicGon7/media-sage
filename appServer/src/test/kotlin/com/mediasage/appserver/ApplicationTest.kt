package com.mediasage.appserver

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.config.*
import io.ktor.server.testing.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ApplicationTest {

    @Test
    fun healthEndpointReturnsOk() = testApplication {
        environment { config = MapApplicationConfig("app.db.path" to ":memory:") }
        application { module() }

        val response = client.get("/health")
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("OK", response.bodyAsText())
    }

    @Test
    fun unknownRouteReturns404() = testApplication {
        environment { config = MapApplicationConfig("app.db.path" to ":memory:") }
        application { module() }

        val response = client.get("/nonexistent")
        assertEquals(HttpStatusCode.NotFound, response.status)
    }

    @Test
    fun assignmentDefaultsReturns7Figures() = testApplication {
        environment { config = MapApplicationConfig("app.db.path" to ":memory:") }
        application { module() }

        val response = client.get("/api/assignments/defaults")
        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.bodyAsText()
        assertTrue(body.contains("Augustine of Hippo"))
        assertTrue(body.contains("Mother Teresa"))
        assertTrue(body.count { it == '{' } == 7)
    }
}
