package com.mediasage.appserver.service

import com.mediasage.appserver.db.HeadlineTable
import com.mediasage.appserver.db.ServerDatabase
import com.mediasage.appserver.repository.HeadlineRepository
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

class HeadlineFetchServiceTest {

    @BeforeTest
    fun setup() {
        ServerDatabase.init(":memory:")
        transaction {
            SchemaUtils.drop(HeadlineTable)
            SchemaUtils.create(HeadlineTable)
        }
    }

    @AfterTest
    fun teardown() {
        transaction { SchemaUtils.drop(HeadlineTable) }
    }

    private val sampleResponse = """
    {
        "totalArticles": 1,
        "articles": [
            {
                "title": "Sample headline",
                "description": "desc",
                "content": "content",
                "url": "https://example.com/article",
                "image": null,
                "publishedAt": "2026-04-19T10:00:00Z",
                "source": { "name": "Reuters", "url": "https://reuters.com" }
            }
        ]
    }
    """.trimIndent()

    private fun createClient(failingCategory: String): HttpClient = HttpClient(
        MockEngine { request ->
            val category = request.url.parameters["category"]
            if (category == failingCategory) {
                respond(
                    content = """{"message":"Internal error"}""",
                    status = HttpStatusCode.InternalServerError,
                    headers = headersOf(HttpHeaders.ContentType, "application/json")
                )
            } else {
                respond(
                    content = sampleResponse,
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/json")
                )
            }
        }
    ) {
        install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
    }

    @Test
    fun fetchAndStoreAll_onFullSuccess_fetchesAndStoresAllSevenCategories() = runTest {
        val headlineRepository = HeadlineRepository()
        val newsApiClient = NewsApiClient(createClient(failingCategory = "none"), "test-key")
        val service = HeadlineFetchService(newsApiClient, headlineRepository, ArticleScraperService())

        val summary = service.fetchAndStoreAll(nowMillis = 1000L)

        assertEquals(HeadlineFetchService.CATEGORIES.toSet(), summary.succeeded.toSet())
        assertEquals(emptyList(), summary.failed)
        HeadlineFetchService.CATEGORIES.forEach { category ->
            val stored = headlineRepository.getStored(category = category)
            assertEquals(1, stored.size)
            assertEquals(listOf(category), stored[0].categories)
        }
    }

    @Test
    fun fetchAndStoreAll_onOneCategoryFailure_otherCategoriesStillPopulate() = runTest {
        val headlineRepository = HeadlineRepository()
        val newsApiClient = NewsApiClient(createClient(failingCategory = "science"), "test-key")
        val service = HeadlineFetchService(newsApiClient, headlineRepository, ArticleScraperService())

        val summary = service.fetchAndStoreAll(nowMillis = 1000L)

        assertEquals(listOf("science"), summary.failed)
        assertEquals(HeadlineFetchService.CATEGORIES.size - 1, summary.succeeded.size)
        assertEquals(emptyList(), headlineRepository.getStored(category = "science"))
        assertEquals(1, headlineRepository.getStored(category = "world").size)
    }

    @Test
    fun fetchAndStoreAll_calledTwice_replacesRatherThanAccumulatesPerCategory() = runTest {
        val headlineRepository = HeadlineRepository()
        val newsApiClient = NewsApiClient(createClient(failingCategory = "none"), "test-key")
        val service = HeadlineFetchService(newsApiClient, headlineRepository, ArticleScraperService())

        service.fetchAndStoreAll(nowMillis = 1000L)
        service.fetchAndStoreAll(nowMillis = 2000L)

        assertEquals(1, headlineRepository.getStored(category = "world").size)
    }
}
