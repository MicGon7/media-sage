package com.mediasage.appserver.repository

import com.mediasage.appserver.db.EncouragementCacheTable
import com.mediasage.appserver.service.EncourageResult
import com.mediasage.appserver.service.EncourageTone
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.insertIgnore
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction

class EncouragementCacheRepository {

    suspend fun getByArticleUrl(articleUrl: String): EncourageResult? = withContext(Dispatchers.IO) {
        transaction {
            EncouragementCacheTable.selectAll()
                .where { EncouragementCacheTable.articleUrl eq articleUrl }
                .singleOrNull()
                ?.toEncourageResult()
        }
    }

    suspend fun insert(articleUrl: String, result: EncourageResult, cachedAt: Long) = withContext(Dispatchers.IO) {
        transaction {
            EncouragementCacheTable.insertIgnore {
                it[EncouragementCacheTable.articleUrl] = articleUrl
                it[summary] = result.summary
                it[quoteText] = result.quoteText
                it[quoteSource] = result.quoteSource
                it[figureName] = result.figureName
                it[figureRole] = result.figureRole
                it[scriptureReference] = result.scriptureReference
                it[scriptureText] = result.scriptureText
                it[explanation] = result.explanation
                it[connectionThemes] = result.connectionThemes.joinToString(",")
                it[matchTheme] = result.matchTheme
                it[tone] = result.tone.name
                it[EncouragementCacheTable.cachedAt] = cachedAt
            }
        }
    }

    private fun ResultRow.toEncourageResult() = EncourageResult(
        summary = this[EncouragementCacheTable.summary],
        quoteText = this[EncouragementCacheTable.quoteText],
        quoteSource = this[EncouragementCacheTable.quoteSource],
        figureName = this[EncouragementCacheTable.figureName],
        figureRole = this[EncouragementCacheTable.figureRole],
        scriptureReference = this[EncouragementCacheTable.scriptureReference],
        scriptureText = this[EncouragementCacheTable.scriptureText],
        explanation = this[EncouragementCacheTable.explanation],
        connectionThemes = this[EncouragementCacheTable.connectionThemes]
            .let { themes -> if (themes.isBlank()) emptyList() else themes.split(",") },
        matchTheme = this[EncouragementCacheTable.matchTheme],
        tone = EncourageTone.valueOf(this[EncouragementCacheTable.tone])
    )
}
