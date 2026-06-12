package com.mediasage.analyst

import com.mediasage.analyst.plugins.configureContentNegotiation
import com.mediasage.analyst.plugins.configureStatusPages
import com.mediasage.analyst.routes.statsRoutes
import com.mediasage.analyst.stats.PipelineStatsReader
import com.mediasage.analyst.stats.RunStats
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

private val testJson = Json { ignoreUnknownKeys = true }

class StatsRoutesTest {

    @Test
    fun defaultsToSevenDayWindow() {
        val reader = FakeStatsReader()
        testStatsApp(reader) {
            val response = client.get("/stats")
            assertEquals(HttpStatusCode.OK, response.status)
            assertEquals(7, reader.lastWindowDays, "Omitting ?days must default to a 7-day window")
        }
    }

    @Test
    fun honoursExplicitDaysParam() {
        val reader = FakeStatsReader()
        testStatsApp(reader) {
            val response = client.get("/stats?days=1")
            assertEquals(HttpStatusCode.OK, response.status)
            assertEquals(1, reader.lastWindowDays, "?days=1 must request a 1-day window (daily digest case)")
        }
    }

    @Test
    fun returnsStatsAsJson() {
        val reader = FakeStatsReader(
            stats = RunStats(
                windowDays = 7,
                totalRuns = 10,
                completedRuns = 8,
                terminalRuns = 9,
                passRate = 0.888,
                avgCostUsd = 0.31,
                avgWallClockSeconds = 257.0,
                avgTurns = 16.0,
            )
        )
        testStatsApp(reader) {
            val response = client.get("/stats")
            assertEquals(HttpStatusCode.OK, response.status)
            val decoded = testJson.decodeFromString(RunStats.serializer(), response.bodyAsText())
            assertEquals(10, decoded.totalRuns)
            assertEquals(8, decoded.completedRuns)
            assertEquals(0.888, decoded.passRate)
            assertEquals(0.31, decoded.avgCostUsd)
        }
    }

    @Test
    fun nonNumericDaysReturns400() {
        testStatsApp(FakeStatsReader()) {
            assertEquals(HttpStatusCode.BadRequest, client.get("/stats?days=abc").status)
        }
    }

    @Test
    fun zeroDaysReturns400() {
        testStatsApp(FakeStatsReader()) {
            assertEquals(HttpStatusCode.BadRequest, client.get("/stats?days=0").status)
        }
    }

    @Test
    fun negativeDaysReturns400() {
        testStatsApp(FakeStatsReader()) {
            assertEquals(HttpStatusCode.BadRequest, client.get("/stats?days=-3").status)
        }
    }
}

private fun testStatsApp(
    reader: PipelineStatsReader,
    block: suspend ApplicationTestBuilder.() -> Unit
) = testApplication {
    application {
        configureContentNegotiation()
        configureStatusPages()
        routing { statsRoutes(reader) }
    }
    block()
}

private class FakeStatsReader(
    private val stats: RunStats = RunStats.empty(7)
) : PipelineStatsReader {
    var lastWindowDays: Int? = null

    override suspend fun stats(windowDays: Int): RunStats {
        lastWindowDays = windowDays
        return stats
    }
}
