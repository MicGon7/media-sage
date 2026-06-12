package com.mediasage.appserver

import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.config.MapApplicationConfig
import io.ktor.server.testing.testApplication
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DailyReflectionRouteTest {

    @Test
    fun rejectsMissingFigureName() = testApplication {
        environment { config = MapApplicationConfig("app.db.path" to ":memory:") }
        application { module() }

        val response = client.post("/api/analysis/daily-reflection") {
            contentType(ContentType.Application.Json)
            setBody("""{"figureId":1,"figureName":"","headlines":[],"tone":"morning"}""")
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
        assertTrue(response.bodyAsText().contains("required"))
    }

    @Test
    fun rejectsZeroFigureId() = testApplication {
        environment { config = MapApplicationConfig("app.db.path" to ":memory:") }
        application { module() }

        val response = client.post("/api/analysis/daily-reflection") {
            contentType(ContentType.Application.Json)
            setBody("""{"figureId":0,"figureName":"C.S. Lewis","headlines":[],"tone":"morning"}""")
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
        assertTrue(response.bodyAsText().contains("required"))
    }
}
