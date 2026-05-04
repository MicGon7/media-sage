package com.mediasage.server.db

import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.JoinType
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
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

    fun fetchAllFigures(): List<FigureRow> = transaction {
        FigureTable.selectAll().map { row ->
            FigureRow(
                id = row[FigureTable.id],
                name = row[FigureTable.name],
                role = row[FigureTable.role],
                century = row[FigureTable.century],
                lifespan = row[FigureTable.lifespan],
                portraitUrl = row[FigureTable.portraitUrl]
            )
        }
    }

    fun updateFigurePortraitUrl(figureId: Long, url: String) = transaction {
        FigureTable.update({ FigureTable.id eq figureId }) {
            it[FigureTable.portraitUrl] = url
        }
    }

    fun migratePortraitUrls(supabaseUrl: String) = transaction {
        val baseUrl = "$supabaseUrl/storage/v1/object/public/portraits"
        val rows = FigureTable.selectAll()
            .where { FigureTable.portraitUrl eq null }
            .map { it[FigureTable.id] }
        rows.forEach { id ->
            FigureTable.update({ FigureTable.id eq id }) {
                it[FigureTable.portraitUrl] = "$baseUrl/$id.webp"
            }
        }
        if (rows.isNotEmpty()) println("Migrated ${rows.size} portrait URLs to Supabase Storage.")
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

data class FigureRow(
    val id: Long,
    val name: String,
    val role: String,
    val century: String,
    val lifespan: String,
    val portraitUrl: String?
)
