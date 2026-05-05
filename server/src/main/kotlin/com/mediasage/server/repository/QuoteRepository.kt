package com.mediasage.server.repository

import com.mediasage.server.db.QuoteTable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction

class QuoteRepository {
    suspend fun getVerifiedByFigureId(figureId: Long): List<QuoteData> = withContext(Dispatchers.IO) {
        transaction {
            QuoteTable.selectAll()
                .where { (QuoteTable.figureId eq figureId) and (QuoteTable.verified eq true) }
                .map {
                    QuoteData(
                        text = it[QuoteTable.text],
                        source = it[QuoteTable.sourceText],
                        themes = it[QuoteTable.themes]
                    )
                }
        }
    }
}

data class QuoteData(val text: String, val source: String, val themes: String)
