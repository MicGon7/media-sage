package com.mediasage.server.service

import com.mediasage.server.db.QuoteCandidate as DbQuoteCandidate
import com.mediasage.server.prompts.EncouragePrompt
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

class ClaudeApiService(
    private val httpClient: HttpClient,
    private val apiKey: String
) {
    private val recentFigures = object : LinkedHashMap<String, Unit>(16, 0.75f, false) {
        override fun removeEldestEntry(eldest: Map.Entry<String, Unit>) = size > RECENT_FIGURES_MAX
    }

    companion object {
        private const val BASE_URL = "https://api.anthropic.com/v1/messages"
        private const val API_VERSION = "2023-06-01"
        private const val MODEL = "claude-sonnet-4-6"
        private const val DEFAULT_MAX_TOKENS = 1024
        private const val RECENT_FIGURES_MAX = 10
        private const val CANDIDATE_POOL_SIZE = 20
        private const val MAX_QUOTES_PER_FIGURE = 2

        private val responseJson = Json {
            ignoreUnknownKeys = true
            isLenient = true
        }
    }

    suspend fun encourageHeadline(
        headlineTitle: String,
        candidates: List<DbQuoteCandidate>,
        locale: String = "en",
        articleText: String? = null
    ): EncourageResult {
        val excluded = synchronized(recentFigures) { recentFigures.keys.toSet() }
        val pool = sampleCandidates(candidates, excluded)
        val userMessage = EncouragePrompt.buildUserMessage(headlineTitle, locale, articleText, pool)
        val raw = callClaude(EncouragePrompt.SYSTEM_PROMPT, userMessage)
        val selection = responseJson.decodeFromString<SelectionResult>(extractJson(raw))
        val selected = resolveSelection(selection.selectedQuoteId, pool) {
            val retryMessage = EncouragePrompt.buildUserMessage(headlineTitle, locale, articleText, pool, strictIds = true)
            val retryRaw = callClaude(EncouragePrompt.SYSTEM_PROMPT, retryMessage)
            responseJson.decodeFromString<SelectionResult>(extractJson(retryRaw)).selectedQuoteId
        }
        synchronized(recentFigures) { recentFigures[selected.figureName] = Unit }
        return EncourageResult(
            summary = selection.summary,
            quoteText = selected.quoteText,
            quoteSource = selected.source,
            figureName = selected.figureName,
            figureRole = selected.figureRole,
            scriptureReference = selection.scriptureReference,
            scriptureText = selection.scriptureText,
            explanation = selection.explanation,
            connectionThemes = selection.connectionThemes,
            matchTheme = selection.matchTheme,
            tone = selection.tone
        )
    }

    private suspend fun resolveSelection(
        quoteId: Long?,
        pool: List<DbQuoteCandidate>,
        retryId: suspend () -> Long?
    ): DbQuoteCandidate =
        (if (quoteId != null) pool.find { it.quoteId == quoteId } else null)
            ?: pool.find { it.quoteId == retryId() }
            ?: throw ClaudeApiException(500, "Claude returned invalid quoteId after retry")

    suspend fun generateDailyReflection(
        systemPrompt: String,
        userMessage: String,
        tone: String
    ): DailyReflectionResult {
        val raw = callClaude(systemPrompt, userMessage, maxTokens = 512)
        val parsed = responseJson.decodeFromString<DailyReflectionRaw>(extractJson(raw))
        return DailyReflectionResult(
            scriptureReference = parsed.scriptureReference,
            scriptureText = parsed.scriptureText,
            reflection = parsed.reflection,
            sources = parsed.sources,
            tone = tone
        )
    }

    private fun sampleCandidates(
        all: List<DbQuoteCandidate>,
        excludedFigures: Set<String>
    ): List<DbQuoteCandidate> {
        val quotesPerFigure = mutableMapOf<String, Int>()
        return all
            .filter { it.figureName !in excludedFigures }
            .shuffled()
            .filter { candidate ->
                val count = quotesPerFigure.getOrDefault(candidate.figureName, 0)
                if (count < MAX_QUOTES_PER_FIGURE) {
                    quotesPerFigure[candidate.figureName] = count + 1
                    true
                } else {
                    false
                }
            }
            .take(CANDIDATE_POOL_SIZE)
    }

    private suspend fun callClaude(
        systemPrompt: String,
        userMessage: String,
        maxTokens: Int = DEFAULT_MAX_TOKENS
    ): String {
        val request = ClaudeRequest(
            model = MODEL,
            maxTokens = maxTokens,
            system = systemPrompt,
            messages = listOf(ClaudeMessage(role = "user", content = userMessage))
        )

        val httpResponse = httpClient.post(BASE_URL) {
            contentType(ContentType.Application.Json)
            header("x-api-key", apiKey)
            header("anthropic-version", API_VERSION)
            setBody(request)
        }

        if (!httpResponse.status.isSuccess()) {
            val errorBody = httpResponse.bodyAsText()
            throw ClaudeApiException(
                statusCode = httpResponse.status.value,
                message = "Claude API error (${httpResponse.status}): $errorBody"
            )
        }

        val claudeResponse = httpResponse.body<ClaudeResponse>()
        return claudeResponse.content.firstOrNull()?.text
            ?: throw ClaudeApiException(500, "Empty response from Claude")
    }

    private fun extractJson(text: String): String {
        val jsonBlockRegex = Regex("```json?\\s*\\n?(.*?)\\n?```", RegexOption.DOT_MATCHES_ALL)
        val match = jsonBlockRegex.find(text)
        return match?.groupValues?.get(1)?.trim() ?: text.trim()
    }
}

// ---- Supporting types ----

@Serializable
data class DailyReflectionRaw(
    @SerialName("scriptureReference") val scriptureReference: String,
    @SerialName("scriptureText") val scriptureText: String,
    val reflection: String,
    val sources: List<String>
)

@Serializable
data class SelectionResult(
    val selectedQuoteId: Long? = null,
    val summary: String? = null,
    val scriptureReference: String,
    val scriptureText: String,
    val explanation: String,
    val connectionThemes: List<String>,
    val matchTheme: String,
    val tone: EncourageTone
)

@Serializable
data class EncourageResult(
    val summary: String? = null,
    val quoteText: String,
    val quoteSource: String,
    val figureName: String,
    val figureRole: String,
    val scriptureReference: String,
    val scriptureText: String,
    val explanation: String,
    val connectionThemes: List<String>,
    val matchTheme: String,
    val tone: EncourageTone,
    val figureImageUrl: String? = null
)

@Serializable
enum class EncourageTone {
    COMFORT,
    EXHORTATION,
    CORRECTION
}

class ClaudeApiException(
    val statusCode: Int,
    override val message: String
) : RuntimeException(message)


