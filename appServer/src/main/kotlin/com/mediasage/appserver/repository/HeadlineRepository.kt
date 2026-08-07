package com.mediasage.appserver.repository

import com.mediasage.appserver.db.HeadlineTable
import com.mediasage.appserver.service.NewsArticle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction

class HeadlineRepository {

    suspend fun replaceCategory(categoryName: String, articles: List<NewsArticle>, fetchedAt: Long) =
        withContext(Dispatchers.IO) {
            transaction {
                HeadlineTable.deleteWhere { builder -> builder.run { category eq categoryName } }
                articles.forEach { article ->
                    HeadlineTable.insert {
                        it[uuid] = article.uuid
                        it[category] = categoryName
                        it[title] = article.title
                        it[description] = article.description
                        it[snippet] = article.snippet
                        it[url] = article.url
                        it[imageUrl] = article.imageUrl
                        it[publishedAt] = article.publishedAt
                        it[sourceText] = article.source
                        it[HeadlineTable.fetchedAt] = fetchedAt
                    }
                }
            }
        }

    suspend fun getStored(category: String? = null, limit: Int = 10): List<NewsArticle> =
        withContext(Dispatchers.IO) {
            transaction {
                if (category != null) {
                    articlesForCategory(category, limit)
                } else {
                    interleaveCategories(limit)
                }
            }
        }

    private fun articlesForCategory(category: String, limit: Int): List<NewsArticle> =
        HeadlineTable.selectAll()
            .where { HeadlineTable.category eq category }
            .orderBy(HeadlineTable.fetchedAt to SortOrder.DESC, HeadlineTable.id to SortOrder.DESC)
            .limit(limit)
            .map { it.toNewsArticle() }

    // A single fetchAndStoreAll() run writes every category's rows with the same fetchedAt, so a
    // flat ORDER BY has no tiebreak power once the table holds more than `limit` rows — the whole
    // result would come from whichever category the DB happens to return first for the tie. Round-
    // robin across categories instead so the Home feed (the only caller that omits `category`)
    // gets a diverse cross-category set rather than an unspecified DB-dependent one.
    private fun interleaveCategories(limit: Int): List<NewsArticle> {
        val categories = HeadlineTable
            .select(HeadlineTable.category)
            .withDistinct()
            .map { it[HeadlineTable.category] }
            .sorted()

        val byCategory = categories.map { articlesForCategory(it, limit) }
        val result = mutableListOf<NewsArticle>()
        var index = 0
        while (result.size < limit && byCategory.any { index < it.size }) {
            byCategory.forEach { articles ->
                if (result.size < limit && index < articles.size) {
                    result += articles[index]
                }
            }
            index++
        }
        return result
    }

    private fun ResultRow.toNewsArticle() = NewsArticle(
        uuid = this[HeadlineTable.uuid],
        title = this[HeadlineTable.title],
        description = this[HeadlineTable.description],
        snippet = this[HeadlineTable.snippet],
        url = this[HeadlineTable.url],
        imageUrl = this[HeadlineTable.imageUrl],
        publishedAt = this[HeadlineTable.publishedAt],
        source = this[HeadlineTable.sourceText],
        categories = listOf(this[HeadlineTable.category])
    )
}
