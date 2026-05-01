package com.mediasage.server.db

import com.mediasage.server.service.ClaudeApiService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.slf4j.LoggerFactory

object QuoteSeeder {

    private val logger = LoggerFactory.getLogger(QuoteSeeder::class.java)
    private const val TARGET_QUOTES_PER_FIGURE = 20

    suspend fun seed(claudeService: ClaudeApiService) = withContext(Dispatchers.IO) {
        val figures = loadFigures()
        if (figures.isEmpty()) {
            logger.info("No figures found — skipping quote seeding (run FigureSeeder first)")
            return@withContext
        }

        var seeded = 0
        var skipped = 0
        figures.forEachIndexed { index, figure ->
            if (hasEnoughQuotes(figure.id)) {
                skipped++
                return@forEachIndexed
            }
            logger.info("Generating quotes for ${figure.name} (${index + 1}/${figures.size})...")
            if (generateAndInsert(claudeService, figure)) seeded++
            if (index < figures.size - 1) delay(1_000)
        }
        logger.info("Quote seeding complete — generated for $seeded figures, skipped $skipped")
    }

    private fun loadFigures(): List<FigureRow> = transaction {
        FigureTable.selectAll().map { row ->
            FigureRow(
                id = row[FigureTable.id],
                name = row[FigureTable.name],
                role = row[FigureTable.role],
                category = row[FigureTable.category],
                century = row[FigureTable.century],
                lifespan = row[FigureTable.lifespan]
            )
        }
    }

    private fun hasEnoughQuotes(figureId: Long): Boolean = transaction {
        QuoteTable.selectAll().where { QuoteTable.figureId eq figureId }.count() >= TARGET_QUOTES_PER_FIGURE
    }

    private suspend fun generateAndInsert(claudeService: ClaudeApiService, figure: FigureRow): Boolean {
        return try {
            val generated = claudeService.generateQuotesForFigure(
                name = figure.name, role = figure.role,
                category = figure.category, century = figure.century, lifespan = figure.lifespan
            )
            val inserted = insertNewQuotes(figure.id, generated)
            logger.info("Seeded $inserted quotes for ${figure.name}")
            true
        } catch (e: Exception) {
            logger.error("Failed to generate quotes for ${figure.name}: ${e.message}")
            false
        }
    }

    private fun insertNewQuotes(
        figureId: Long,
        quotes: List<com.mediasage.server.service.GeneratedQuote>
    ): Int = transaction {
        quotes.count { quote ->
            val exists = QuoteTable.selectAll().where {
                (QuoteTable.figureId eq figureId) and (QuoteTable.text eq quote.text)
            }.count() > 0
            if (!exists) {
                QuoteTable.insert {
                    it[QuoteTable.figureId] = figureId
                    it[text] = quote.text
                    it[sourceText] = quote.source
                    it[themes] = quote.themes.joinToString(",")
                    it[verified] = false
                }
            }
            !exists
        }
    }
}

private data class FigureRow(
    val id: Long,
    val name: String,
    val role: String,
    val category: String,
    val century: String,
    val lifespan: String
)
