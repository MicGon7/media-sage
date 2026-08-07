package com.mediasage.appserver.repository

import com.mediasage.appserver.db.HeadlineTable
import com.mediasage.appserver.service.NewsArticle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
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
                val query = if (category != null) {
                    HeadlineTable.selectAll().where { HeadlineTable.category eq category }
                } else {
                    HeadlineTable.selectAll()
                }
                query.orderBy(HeadlineTable.fetchedAt, SortOrder.DESC)
                    .limit(limit)
                    .map { it.toNewsArticle() }
            }
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
