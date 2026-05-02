package com.mediasage.server.db

import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.JoinType
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import java.io.File

object ServerDatabase {

    fun init(dbPath: String = "mediasage-server.db") {
        val dbFile = File(dbPath)
        Database.connect(
            url = "jdbc:sqlite:${dbFile.absolutePath}",
            driver = "org.sqlite.JDBC"
        )
        transaction {
            SchemaUtils.createMissingTablesAndColumns(FigureTable, QuoteTable)
        }
    }

    fun fetchQuoteCandidates(): List<QuoteCandidate> = transaction {
        QuoteTable.join(FigureTable, JoinType.INNER, onColumn = QuoteTable.figureId, otherColumn = FigureTable.id)
            .selectAll()
            .map { row ->
                QuoteCandidate(
                    quoteId = row[QuoteTable.id],
                    figureId = row[FigureTable.id],
                    figureName = row[FigureTable.name],
                    figureRole = row[FigureTable.role],
                    quoteText = row[QuoteTable.text],
                    source = row[QuoteTable.sourceText],
                    themes = row[QuoteTable.themes]
                )
            }
    }

    fun findQuote(quoteId: Long): QuoteCandidate? = transaction {
        QuoteTable.join(FigureTable, JoinType.INNER, onColumn = QuoteTable.figureId, otherColumn = FigureTable.id)
            .selectAll()
            .where { QuoteTable.id eq quoteId }
            .map { row ->
                QuoteCandidate(
                    quoteId = row[QuoteTable.id],
                    figureId = row[FigureTable.id],
                    figureName = row[FigureTable.name],
                    figureRole = row[FigureTable.role],
                    quoteText = row[QuoteTable.text],
                    source = row[QuoteTable.sourceText],
                    themes = row[QuoteTable.themes]
                )
            }.singleOrNull()
    }
}

data class QuoteCandidate(
    val quoteId: Long,
    val figureId: Long,
    val figureName: String,
    val figureRole: String,
    val quoteText: String,
    val source: String,
    val themes: String
)
