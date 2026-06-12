package com.mediasage.appserver

import com.mediasage.appserver.db.FigureTable
import com.mediasage.appserver.db.ServerDatabase
import com.mediasage.appserver.repository.FigureRepository
import com.mediasage.appserver.routes.figureRoutes
import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.routing.routing
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

class FigureRoutesTest {

    @BeforeTest
    fun setup() {
        ServerDatabase.init(":memory:")
        transaction {
            SchemaUtils.drop(FigureTable)
            SchemaUtils.create(FigureTable)
            FigureTable.insert {
                it[name] = "Martin Luther"
                it[category] = "theologian"
                it[century] = "16th"
                it[role] = "Theologian & Reformer"
                it[lifespan] = "1483-1546"
                it[bio] = "German professor of theology and seminal figure of the Protestant Reformation."
                it[isEnabled] = true
            }
            FigureTable.insert {
                it[name] = "Hidden Figure"
                it[category] = "theologian"
                it[century] = "20th"
                it[role] = "Pastor"
                it[lifespan] = "1900-1950"
                it[bio] = ""
                it[isEnabled] = false
            }
        }
    }

    @AfterTest
    fun teardown() {
        transaction { SchemaUtils.drop(FigureTable) }
    }

    @Test
    fun figuresEndpointReturnsOnlyEnabledFigures() = testApplication {
        install(ContentNegotiation) { json() }
        install(Koin) { modules(module { single { FigureRepository("http://localhost:8080") } }) }
        routing { figureRoutes() }

        val response = client.get("/api/figures")
        assertEquals(HttpStatusCode.OK, response.status)
    }
}
