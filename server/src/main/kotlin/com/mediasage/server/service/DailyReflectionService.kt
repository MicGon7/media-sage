package com.mediasage.server.service

import com.mediasage.server.repository.QuoteData
import com.mediasage.server.repository.QuoteRepository

class DailyReflectionService(
    private val claudeApiService: ClaudeApiService,
    private val quoteRepository: QuoteRepository
) {
    suspend fun generate(
        figureId: Long,
        figureName: String,
        headlines: List<String>,
        tone: String
    ): DailyReflectionResult {
        val allQuotes = quoteRepository.getVerifiedByFigureId(figureId)
        val scored = scoreByTheme(allQuotes, headlines)
        val top = scored.take(MAX_QUOTES)

        val systemPrompt = buildSystemPrompt(figureName)
        val userMessage = buildUserMessage(figureName, top, headlines, tone)

        return claudeApiService.generateDailyReflection(systemPrompt, userMessage, tone)
    }

    private fun scoreByTheme(quotes: List<QuoteData>, headlines: List<String>): List<QuoteData> {
        if (headlines.isEmpty()) return quotes
        val headlineWords = headlines.joinToString(" ")
            .lowercase()
            .split(Regex("[^a-z]+"))
            .filter { it.length > 3 }
            .toSet()
        return quotes.sortedByDescending { quote ->
            quote.themes.lowercase().split(Regex("[^a-z]+"))
                .count { it in headlineWords }
        }
    }

    private fun buildSystemPrompt(figureName: String) = """
        You are generating a devotional reflection in the voice of $figureName.
        You MUST draw only from the verified quotes provided below — do not invent quotes, paraphrase beyond their meaning, or introduce theological ideas not present in the source material.
        The sources listed are the only texts you may draw from.
        Respond ONLY with valid JSON — no markdown, no explanation outside the JSON.
    """.trimIndent()

    private fun buildUserMessage(
        figureName: String,
        quotes: List<QuoteData>,
        headlines: List<String>,
        tone: String
    ) = buildString {
        appendLine("## Verified Quotes from $figureName")
        appendLine("Draw ONLY from these quotes. Do not invent.")
        appendLine()
        quotes.forEach { q ->
            appendLine("Source: ${q.source}")
            appendLine("Quote: \"${q.text}\"")
            appendLine()
        }
        if (headlines.isNotEmpty()) {
            appendLine("## Today's Headlines (for thematic context only)")
            headlines.forEach { appendLine("- $it") }
            appendLine()
        }
        appendLine("## Instructions")
        appendLine("Write a $tone devotional reflection in the voice of $figureName.")
        appendLine("- Include a scripture reference and the full verse text")
        appendLine("- Write 2-3 sentences of reflection grounded only in the quotes above")
        appendLine("- List the source titles you drew from")
        appendLine()
        appendLine(RESPONSE_FORMAT)
    }

    companion object {
        private const val MAX_QUOTES = 5
        private val RESPONSE_FORMAT = """
            Respond ONLY with JSON in this exact format:
            {
              "scriptureReference": "<e.g. Psalm 46:10>",
              "scriptureText": "<full verse text>",
              "reflection": "<2-3 sentence reflection>",
              "sources": ["<source title>"]
            }
        """.trimIndent()
    }
}

data class DailyReflectionResult(
    val scriptureReference: String,
    val scriptureText: String,
    val reflection: String,
    val sources: List<String>,
    val tone: String
)
