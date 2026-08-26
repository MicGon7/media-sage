package com.mediasage.appserver

import com.mediasage.appserver.db.ClaudeCallLimitTable
import com.mediasage.appserver.db.EncouragementCacheTable
import com.mediasage.appserver.db.FigureTable
import com.mediasage.appserver.db.QuoteTable
import com.mediasage.appserver.db.ServerDatabase
import com.mediasage.appserver.repository.ClaudeCallLimitRepository
import com.mediasage.appserver.repository.EncouragementCacheRepository
import com.mediasage.appserver.plugins.ErrorResponse
import com.mediasage.appserver.repository.FigureRepository
import com.mediasage.appserver.routes.analysisRoutes
import com.mediasage.appserver.service.ArticleScraperService
import com.mediasage.appserver.service.ClaudeApiClient
import com.mediasage.appserver.service.DailyLimitExceededException
import com.mediasage.appserver.service.EncourageResult
import com.mediasage.appserver.service.EncourageTone
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation as ClientContentNegotiation
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.response.respond
import io.ktor.server.routing.routing
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.transactions.transaction
import org.koin.core.qualifier.named
import org.koin.dsl.module
import org.koin.ktor.plugin.Koin
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

private fun claudeResponseBody(quoteId: Long) = """
{
    "id": "msg_1",
    "type": "message",
    "role": "assistant",
    "model": "claude-sonnet-4-6",
    "content": [{"type":"text","text":"{\"selectedQuoteId\":$quoteId,\"summary\":\"Stay hopeful\",\"scriptureReference\":\"Psalm 46:10\",\"scriptureText\":\"Be still, and know that I am God.\",\"explanation\":\"A call to trust.\",\"connectionThemes\":[\"peace\"],\"matchTheme\":\"peace\",\"tone\":\"COMFORT\"}"}],
    "usage": {"input_tokens": 10, "output_tokens": 10}
}
"""

private fun sampleEncourageResult(figureName: String) = EncourageResult(
    summary = "Stay hopeful",
    quoteText = "Be still and know that I am God.",
    quoteSource = "Psalms",
    figureName = figureName,
    figureRole = "Prophet",
    scriptureReference = "Psalm 46:10",
    scriptureText = "Be still, and know that I am God.",
    explanation = "A call to trust.",
    connectionThemes = listOf("peace"),
    matchTheme = "peace",
    tone = EncourageTone.COMFORT
)

private fun mockClaudeApiClient(quoteId: Long, onCall: () -> Unit): ClaudeApiClient {
    val httpClient = HttpClient(MockEngine { _ ->
        onCall()
        respond(
            content = claudeResponseBody(quoteId),
            status = HttpStatusCode.OK,
            headers = headersOf(io.ktor.http.HttpHeaders.ContentType, "application/json")
        )
    }) {
        install(ClientContentNegotiation) {
            json(Json { ignoreUnknownKeys = true })
        }
    }
    return ClaudeApiClient(httpClient, "test-key")
}

private fun ApplicationTestBuilder.installRoutes(
    quoteId: Long,
    claudeCallCount: () -> Unit,
    dailyClaudeCallLimit: Int = 300
) {
    install(ContentNegotiation) { json() }
    install(StatusPages) {
        exception<DailyLimitExceededException> { call, cause ->
            call.respond(HttpStatusCode.TooManyRequests, ErrorResponse(429, cause.message))
        }
    }
    install(Koin) {
        modules(
            module {
                single { mockClaudeApiClient(quoteId, claudeCallCount) }
                single { ArticleScraperService() }
                single { FigureRepository("http://localhost:8080") }
                single { EncouragementCacheRepository() }
                single { ClaudeCallLimitRepository() }
                single<Int>(named("dailyClaudeCallLimit")) { dailyClaudeCallLimit }
            }
        )
    }
    routing { analysisRoutes() }
}

class EncourageRoutesTest {

    private var quoteId: Long = 0

    @BeforeTest
    fun setup() {
        ServerDatabase.init(":memory:")
        transaction {
            SchemaUtils.drop(FigureTable, QuoteTable, EncouragementCacheTable, ClaudeCallLimitTable)
            SchemaUtils.create(FigureTable, QuoteTable, EncouragementCacheTable, ClaudeCallLimitTable)
            val figureId = FigureTable.insert {
                it[name] = "Test Figure"
                it[category] = "theologian"
                it[century] = "16th"
                it[role] = "Prophet"
            }[FigureTable.id]
            quoteId = QuoteTable.insert {
                it[QuoteTable.figureId] = figureId
                it[text] = "Be still and know that I am God."
                it[sourceText] = "Psalms"
            }[QuoteTable.id]
        }
    }

    @AfterTest
    fun teardown() {
        transaction {
            SchemaUtils.drop(FigureTable, QuoteTable, EncouragementCacheTable, ClaudeCallLimitTable)
        }
    }

    @Test
    fun cacheLookupIsScopedPerLocaleNotJustArticleUrl() = runTest {
        val repository = EncouragementCacheRepository()
        val enResult = sampleEncourageResult(figureName = "English Figure")
        val esResult = sampleEncourageResult(figureName = "Spanish Figure")
        val articleUrl = "https://example.com/locale"

        repository.insert(articleUrl, "en", enResult, cachedAt = 1L)
        repository.insert(articleUrl, "es", esResult, cachedAt = 2L)

        assertEquals("English Figure", repository.getByArticleUrlAndLocale(articleUrl, "en")?.figureName)
        assertEquals("Spanish Figure", repository.getByArticleUrlAndLocale(articleUrl, "es")?.figureName)
        assertEquals(null, repository.getByArticleUrlAndLocale(articleUrl, "fr"))
    }

    @Test
    fun newArticleTriggersClaudeCallAndIsCached() = testApplication {
        var callCount = 0
        installRoutes(quoteId, claudeCallCount = { callCount++ })

        val response = client.post("/api/analysis/encourage") {
            contentType(ContentType.Application.Json)
            setBody("""{"headlineTitle":"Markets rally","articleUrl":"https://example.com/a"}""")
        }

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(1, callCount)
    }

    @Test
    fun repeatedArticleUrlDoesNotTriggerSecondClaudeCall() = testApplication {
        var callCount = 0
        installRoutes(quoteId, claudeCallCount = { callCount++ })

        repeat(2) {
            val response = client.post("/api/analysis/encourage") {
                contentType(ContentType.Application.Json)
                setBody("""{"headlineTitle":"Markets rally","articleUrl":"https://example.com/b"}""")
            }
            assertEquals(HttpStatusCode.OK, response.status)
        }

        assertEquals(1, callCount)
    }

    @Test
    fun dailyLimitBlocksFurtherCallsWithErrorResponse() = testApplication {
        var callCount = 0
        installRoutes(quoteId, claudeCallCount = { callCount++ }, dailyClaudeCallLimit = 1)

        val first = client.post("/api/analysis/encourage") {
            contentType(ContentType.Application.Json)
            setBody("""{"headlineTitle":"Headline one","articleUrl":"https://example.com/c"}""")
        }
        assertEquals(HttpStatusCode.OK, first.status)

        val second = client.post("/api/analysis/encourage") {
            contentType(ContentType.Application.Json)
            setBody("""{"headlineTitle":"Headline two","articleUrl":"https://example.com/d"}""")
        }

        assertEquals(HttpStatusCode.TooManyRequests, second.status)
        assertEquals(1, callCount)
    }
}
