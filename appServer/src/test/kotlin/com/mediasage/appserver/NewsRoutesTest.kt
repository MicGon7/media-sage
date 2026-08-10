package com.mediasage.appserver

import com.mediasage.appserver.db.HeadlineTable
import com.mediasage.appserver.db.ServerDatabase
import com.mediasage.appserver.repository.HeadlineRepository
import com.mediasage.appserver.routes.newsRoutes
import com.mediasage.appserver.service.ArticleScraperService
import com.mediasage.appserver.service.NewsApiClient
import com.mediasage.appserver.service.NewsArticle
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
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

class NewsRoutesTest {

    @BeforeTest
    fun setup() {
        ServerDatabase.init(":memory:")
        transaction {
            SchemaUtils.drop(HeadlineTable)
            SchemaUtils.create(HeadlineTable)
            HeadlineTable.insert {
                it[uuid] = "world-1"
                it[category] = "world"
                it[title] = "World headline"
                it[url] = "https://example.com/world"
                it[fetchedAt] = 1000L
            }
            HeadlineTable.insert {
                it[uuid] = "business-1"
                it[category] = "business"
                it[title] = "Business headline"
                it[url] = "https://example.com/business"
                it[fetchedAt] = 1000L
            }
        }
    }

    @AfterTest
    fun teardown() {
        transaction { SchemaUtils.drop(HeadlineTable) }
    }

    // The /headlines endpoint must not call the live provider at all, so injecting a client that
    // errors on any request doubles as proof reads are served purely from the cache.
    private fun unreachableNewsApiClient() = NewsApiClient(
        HttpClient(MockEngine { error("live provider should not be called by /headlines") }),
        "unused-key"
    )

    private fun testKoinModule() = module {
        single { HeadlineRepository() }
        single { unreachableNewsApiClient() }
        single { ArticleScraperService() }
    }

    @Test
    fun headlinesEndpoint_returnsStoredArticlesAcrossAllCategories() = testApplication {
        install(io.ktor.server.plugins.contentnegotiation.ContentNegotiation) { json() }
        install(Koin) { modules(testKoinModule()) }
        routing { newsRoutes() }

        val client = createClient { install(ContentNegotiation) { json() } }
        val response = client.get("/api/news/headlines")

        assertEquals(HttpStatusCode.OK, response.status)
        val articles = response.body<List<NewsArticle>>()
        assertEquals(2, articles.size)
        assertEquals(setOf("world", "business"), articles.map { it.categories.single() }.toSet())
    }

    @Test
    fun headlinesEndpoint_filtersByCategoryQueryParam() = testApplication {
        install(io.ktor.server.plugins.contentnegotiation.ContentNegotiation) { json() }
        install(Koin) { modules(testKoinModule()) }
        routing { newsRoutes() }

        val client = createClient { install(ContentNegotiation) { json() } }
        val response = client.get("/api/news/headlines?category=business")

        assertEquals(HttpStatusCode.OK, response.status)
        val articles = response.body<List<NewsArticle>>()
        assertEquals(1, articles.size)
        assertEquals(listOf("business"), articles[0].categories)
    }

    private fun seedTiedCategory(category: String, count: Int) = transaction {
        repeat(count) { i ->
            HeadlineTable.insert {
                it[uuid] = "$category-$i"
                it[HeadlineTable.category] = category
                it[title] = "${category.replaceFirstChar(Char::uppercase)} headline $i"
                it[url] = "https://example.com/$category-$i"
                it[fetchedAt] = 1000L
            }
        }
    }

    // A single fetchAndStoreAll() run writes every category's rows with the same fetchedAt, so
    // once a category holds more rows than `limit` a flat ORDER BY has no tiebreak power and can
    // return every slot from one category. Seed two categories well past `limit` here to prove
    // the no-category read still spreads across categories instead of collapsing to one.
    @Test
    fun headlinesEndpoint_withoutCategory_interleavesAcrossTiedCategories() = testApplication {
        seedTiedCategory("nation", count = 5)
        seedTiedCategory("technology", count = 5)

        install(io.ktor.server.plugins.contentnegotiation.ContentNegotiation) { json() }
        install(Koin) { modules(testKoinModule()) }
        routing { newsRoutes() }

        val client = createClient { install(ContentNegotiation) { json() } }
        val response = client.get("/api/news/headlines?limit=4")

        assertEquals(HttpStatusCode.OK, response.status)
        val articles = response.body<List<NewsArticle>>()
        assertEquals(4, articles.size)
        assertEquals(4, articles.map { it.categories.single() }.toSet().size)
    }
}
