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
        tone: String,
        dayOfWeek: String = "",
        previousScriptures: List<String> = emptyList(),
        previousReflections: List<String> = emptyList()
    ): DailyReflectionResult {
        val allQuotes = quoteRepository.getVerifiedByFigureId(figureId)
        val scored = scoreByTheme(allQuotes, headlines)
        val top = scored.take(MAX_QUOTES)

        val systemPrompt = buildSystemPrompt(figureName)
        val userMessage = buildUserMessage(figureName, top, headlines, tone, dayOfWeek, previousScriptures, previousReflections)

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
        The verified quotes below anchor the theological voice and grounding — draw from your knowledge of these specific source works, letting the quotes shape the direction and register of the reflection.
        Do not invent quotes or attribute specific words to $figureName that are not in the verified set.
        Respond ONLY with valid JSON — no markdown, no explanation outside the JSON.
    """.trimIndent()

    private fun buildUserMessage(
        figureName: String,
        quotes: List<QuoteData>,
        headlines: List<String>,
        tone: String,
        dayOfWeek: String = "",
        previousScriptures: List<String> = emptyList(),
        previousReflections: List<String> = emptyList()
    ) = buildString {
        appendLine("## Verified Quotes from $figureName")
        appendLine("Draw from your knowledge of these source works, letting these quotes anchor the theological voice and direction.")
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
        append(buildContextBlock(tone, dayOfWeek, previousScriptures, previousReflections))
        appendLine("## Instructions")
        appendLine("Write a $tone devotional reflection in the voice of $figureName.")
        appendLine("- Include a scripture reference and the full verse text")
        appendLine("- Write 2-3 sentences of reflection grounded in the source works above")
        appendLine("- List the source titles you drew from")
        appendLine()
        appendLine(RESPONSE_FORMAT)
    }

    private fun buildContextBlock(
        tone: String,
        dayOfWeek: String,
        previousScriptures: List<String>,
        previousReflections: List<String>
    ) = buildString {
        val dayContext = if (dayOfWeek.isNotBlank()) "$dayOfWeek, " else ""
        appendLine("## Context")
        appendLine("Today is $dayContext$tone.")
        if (previousScriptures.isNotEmpty()) {
            appendLine()
            appendLine(PREVIOUS_REFLECTION_INSTRUCTION)
            previousScriptures.zip(previousReflections).forEach { (scripture, reflection) ->
                appendLine("- Scripture: $scripture | Reflection: $reflection")
            }
        }
        appendLine()
    }

    companion object {
        private const val MAX_QUOTES = 5
        private const val PREVIOUS_REFLECTION_INSTRUCTION =
            "An earlier reflection was shared today. Do NOT reuse the same verse. " +
            "You may revisit a theme if the headlines call for it, but bring a fresh angle, " +
            "a different application, or a deeper dimension — avoid repeating the same argument:"
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
