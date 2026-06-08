package com.mediasage.server.service

import com.mediasage.server.repository.QuoteData
import com.mediasage.server.repository.QuoteRepository

class DailyReflectionService(
    private val claudeApiService: ClaudeApiService,
    private val quoteRepository: QuoteRepository
) {
    suspend fun generate(request: GenerateRequest): DailyReflectionResult {
        val allQuotes = quoteRepository.getVerifiedByFigureId(request.figureId)
        val scored = scoreByTheme(allQuotes, request.headlines)
        val top = scored.take(MAX_QUOTES)

        val systemPrompt = buildSystemPrompt(request.figureName)
        val userMessage = buildUserMessage(request, top)

        return claudeApiService.generateDailyReflection(systemPrompt, userMessage, request.tone)
    }

    data class GenerateRequest(
        val figureId: Long,
        val figureName: String,
        val headlines: List<String> = emptyList(),
        val tone: String = "morning",
        val dayOfWeek: String = "",
        val previousScriptures: List<String> = emptyList(),
        val previousReflections: List<String> = emptyList(),
        val theme: String? = null
    )

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

    private fun buildUserMessage(request: GenerateRequest, quotes: List<QuoteData>) = buildString {
        appendLine("## Verified Quotes from ${request.figureName}")
        appendLine("Draw from your knowledge of these source works, letting these quotes anchor the theological voice and direction.")
        appendLine()
        quotes.forEach { q ->
            appendLine("Source: ${q.source}")
            appendLine("Quote: \"${q.text}\"")
            appendLine()
        }
        if (request.headlines.isNotEmpty()) {
            appendLine("## Today's Headlines (for thematic context only)")
            request.headlines.forEach { appendLine("- $it") }
            appendLine()
        }
        append(buildContextBlock(request.tone, request.dayOfWeek, request.previousScriptures, request.previousReflections, request.theme))
        appendLine("## Instructions")
        appendLine("Write a ${request.tone} devotional reflection in the voice of ${request.figureName}.")
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
        previousReflections: List<String>,
        theme: String? = null
    ) = buildString {
        val dayContext = if (dayOfWeek.isNotBlank()) "$dayOfWeek, " else ""
        appendLine("## Context")
        appendLine("Today is $dayContext$tone.")
        if (!theme.isNullOrBlank()) {
            appendLine("Today the reader is carrying a sense of ${theme.lowercase()} — let that shape your reflection.")
        }
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
