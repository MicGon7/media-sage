package com.mediasage.appserver

import com.mediasage.appserver.db.FigureTable
import com.mediasage.appserver.db.ServerDatabase
import com.mediasage.appserver.repository.FigureRepository
import com.mediasage.appserver.repository.FiguresResponse
import com.mediasage.appserver.routes.figureRoutes
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.testing.testApplication
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.transactions.transaction
import org.koin.dsl.module
import org.koin.ktor.plugin.Koin
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

class FigureSinceRouteTest {

    private val oldTimestamp = 1_000_000L
    private val recentTimestamp = 9_000_000L

    @BeforeTest
    fun setup() {
        ServerDatabase.init(":memory:")
        transaction {
            SchemaUtils.drop(FigureTable)
            SchemaUtils.create(FigureTable)
            FigureTable.insert {
                it[name] = "Augustine"
                it[category] = "theologian"
                it[century] = "4th"
                it[role] = "Bishop & Theologian"
                it[lifespan] = "354-430"
                it[bio] = ""
                it[isEnabled] = true
                it[updatedAt] = oldTimestamp
            }
            FigureTable.insert {
                it[name] = "C.S. Lewis"
                it[category] = "author"
                it[century] = "20th"
                it[role] = "Author & Apologist"
                it[lifespan] = "1898-1963"
                it[bio] = ""
                it[isEnabled] = true
                it[updatedAt] = recentTimestamp
            }
        }
    }

    @AfterTest
    fun teardown() {
        transaction { SchemaUtils.drop(FigureTable) }
    }

    @Test
    fun noSinceParamReturnsAllEnabledFigures() = testApplication {
        install(io.ktor.server.plugins.contentnegotiation.ContentNegotiation) { json() }
        install(Koin) { modules(module { single { FigureRepository("http://localhost:8080") } }) }
        routing { figureRoutes() }

        val client = createClient { install(ContentNegotiation) { json() } }
        val response = client.get("/api/figures")
        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.body<FiguresResponse>()
        assertEquals(2, body.figures.size)
    }

    @Test
    fun sincePastOldTimestampReturnsOnlyNewerFigures() = testApplication {
        install(io.ktor.server.plugins.contentnegotiation.ContentNegotiation) { json() }
        install(Koin) { modules(module { single { FigureRepository("http://localhost:8080") } }) }
        routing { figureRoutes() }

        val client = createClient { install(ContentNegotiation) { json() } }
        val response = client.get("/api/figures?since=$oldTimestamp")
        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.body<FiguresResponse>()
        assertEquals(1, body.figures.size)
        assertEquals("C.S. Lewis", body.figures.first().name)
    }

    @Test
    fun sinceFutureTimestampReturnsEmptyList() = testApplication {
        install(io.ktor.server.plugins.contentnegotiation.ContentNegotiation) { json() }
        install(Koin) { modules(module { single { FigureRepository("http://localhost:8080") } }) }
        routing { figureRoutes() }

        val client = createClient { install(ContentNegotiation) { json() } }
        val response = client.get("/api/figures?since=9999999999999")
        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.body<FiguresResponse>()
        assertEquals(0, body.figures.size)
    }
}
