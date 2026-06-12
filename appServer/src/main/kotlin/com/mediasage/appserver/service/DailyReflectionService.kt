package com.mediasage.appserver.service

import com.mediasage.appserver.prompts.DailyReflectionPrompt
import com.mediasage.appserver.prompts.ReflectionTheme
import com.mediasage.appserver.repository.QuoteData
import com.mediasage.appserver.repository.QuoteRepository

class DailyReflectionService(
    private val claudeApiService: ClaudeApiService,
    private val quoteRepository: QuoteRepository
) {
    suspend fun generate(request: DailyReflectionRequest): DailyReflectionResult {
        val allQuotes = quoteRepository.getVerifiedByFigureId(request.figureId)
        val scored = scoreByTheme(allQuotes, request.headlines)
        val top = scored.take(MAX_QUOTES)

        val systemPrompt = DailyReflectionPrompt.buildSystemPrompt(request.figureName)
        val userMessage = DailyReflectionPrompt.buildUserMessage(
            DailyReflectionPrompt.Params(
                figureName = request.figureName,
                quotes = top,
                headlines = request.headlines,
                tone = request.tone,
                dayOfWeek = request.dayOfWeek,
                previousScriptures = request.previousScriptures,
                previousReflections = request.previousReflections,
                theme = request.theme
            )
        )

        return claudeApiService.generateDailyReflection(systemPrompt, userMessage, request.tone)
    }

    data class DailyReflectionRequest(
        val figureId: Long,
        val figureName: String,
        val headlines: List<String> = emptyList(),
        val tone: String = "morning",
        val dayOfWeek: String = "",
        val previousScriptures: List<String> = emptyList(),
        val previousReflections: List<String> = emptyList(),
        val theme: ReflectionTheme? = null
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

    companion object {
        private const val MAX_QUOTES = 5
    }
}

data class DailyReflectionResult(
    val scriptureReference: String,
    val scriptureText: String,
    val insight: String,
    val implication: String,
    val inspiration: String,
    val sources: List<String>,
    val tone: String
)
