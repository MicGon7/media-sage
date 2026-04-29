package com.mediasage.server.service

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsChannel
import io.ktor.client.statement.bodyAsText
import io.ktor.utils.io.ByteReadChannel
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.utils.io.readUTF8Line
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.json.Json

class ClaudeApiService(
    private val httpClient: HttpClient,
    private val apiKey: String
) {
    companion object {
        private const val BASE_URL = "https://api.anthropic.com/v1/messages"
        private const val API_VERSION = "2023-06-01"
        private const val MODEL = "claude-sonnet-4-6"
        private const val MAX_TOKENS = 1024
        const val FIELD_DELIMITER = "---FIELD---"
        val STREAM_FIELD_NAMES = listOf(
            "MATCH_THEME", "TONE", "SUMMARY", "QUOTE", "FIGURE_NAME",
            "FIGURE_ROLE", "SCRIPTURE_REF", "SCRIPTURE_TEXT", "EXPLANATION", "CONNECTION_THEMES"
        )

        private val responseJson = Json {
            ignoreUnknownKeys = true
            isLenient = true
        }

        private val streamingJson = Json {
            ignoreUnknownKeys = true
            isLenient = true
        }
    }

    suspend fun encourageHeadline(
        headlineTitle: String,
        locale: String = "en",
        articleText: String? = null,
        recentFigures: List<String> = emptyList()
    ): EncourageResult {
        val userMessage = buildEncourageMessage(headlineTitle, locale, articleText, recentFigures)
        val response = callClaude(ENCOURAGE_SYSTEM_PROMPT, userMessage)
        return responseJson.decodeFromString<EncourageResult>(extractJson(response))
    }

    fun encourageHeadlineStream(
        headlineTitle: String,
        locale: String = "en",
        articleText: String? = null,
        recentFigures: List<String> = emptyList()
    ): Flow<Pair<String, String>> = flow {
        val httpResponse = httpClient.post(BASE_URL) {
            contentType(ContentType.Application.Json)
            header("x-api-key", apiKey)
            header("anthropic-version", API_VERSION)
            setBody(buildStreamingRequest(headlineTitle, locale, articleText, recentFigures))
        }
        if (!httpResponse.status.isSuccess()) {
            val errorBody = httpResponse.bodyAsText()
            throw ClaudeApiException(httpResponse.status.value, "Claude streaming error: $errorBody")
        }
        val buffer = StringBuilder()
        val fieldIndex = readStreamIntoBuffer(httpResponse.bodyAsChannel(), buffer) { emit(it) }
        if (buffer.isNotEmpty() && fieldIndex < STREAM_FIELD_NAMES.size) {
            emit(STREAM_FIELD_NAMES[fieldIndex] to buffer.toString())
        }
    }

    private suspend fun readStreamIntoBuffer(
        channel: ByteReadChannel,
        buffer: StringBuilder,
        emit: suspend (Pair<String, String>) -> Unit
    ): Int {
        var fieldIndex = 0
        var streamDone = false
        while (!channel.isClosedForRead && !streamDone) {
            val line = channel.readUTF8Line()
            if (line == null) {
                streamDone = true
            } else if (line.startsWith("data: ")) {
                val (newIdx, done) = processDataLine(line.removePrefix("data: "), buffer, fieldIndex, emit)
                fieldIndex = newIdx
                streamDone = done
            }
        }
        return fieldIndex
    }

    private suspend fun processDataLine(
        data: String,
        buffer: StringBuilder,
        fieldIndex: Int,
        emit: suspend (Pair<String, String>) -> Unit
    ): Pair<Int, Boolean> {
        if (data == "[DONE]") return Pair(fieldIndex, true)
        val text = extractTextFromDelta(data) ?: return Pair(fieldIndex, false)
        buffer.append(text)
        return Pair(flushDelimitedBuffer(buffer, fieldIndex, emit), false)
    }

    private fun buildStreamingRequest(
        headlineTitle: String,
        locale: String,
        articleText: String?,
        recentFigures: List<String>
    ) = ClaudeRequest(
        model = MODEL,
        maxTokens = MAX_TOKENS,
        stream = true,
        system = ENCOURAGE_STREAM_SYSTEM_PROMPT,
        messages = listOf(ClaudeMessage("user", buildEncourageMessage(headlineTitle, locale, articleText, recentFigures)))
    )

    private fun extractTextFromDelta(data: String): String? {
        val delta = runCatching { streamingJson.decodeFromString<ClaudeStreamDelta>(data) }.getOrNull()
            ?: return null
        return if (delta.type == "content_block_delta" && delta.delta?.type == "text_delta") {
            delta.delta.text
        } else null
    }

    private suspend fun flushDelimitedBuffer(
        buffer: StringBuilder,
        startIndex: Int,
        emit: suspend (Pair<String, String>) -> Unit
    ): Int {
        var idx = startIndex
        var delimIdx = buffer.indexOf(FIELD_DELIMITER)
        while (delimIdx >= 0) {
            val before = buffer.substring(0, delimIdx)
            if (before.isNotEmpty() && idx < STREAM_FIELD_NAMES.size) emit(STREAM_FIELD_NAMES[idx] to before)
            buffer.delete(0, delimIdx + FIELD_DELIMITER.length)
            idx++
            delimIdx = buffer.indexOf(FIELD_DELIMITER)
        }
        val safeLength = buffer.length - FIELD_DELIMITER.length
        if (safeLength > 0 && idx < STREAM_FIELD_NAMES.size) {
            emit(STREAM_FIELD_NAMES[idx] to buffer.substring(0, safeLength))
            buffer.delete(0, safeLength)
        }
        return idx
    }

    @Deprecated("Use encourageHeadline instead — TODO MS-46")
    suspend fun matchQuoteToHeadline(
        headlineTitle: String,
        candidateQuotes: List<QuoteCandidate>
    ): MatchResult {
        val userMessage = buildMatchMessage(headlineTitle, candidateQuotes)
        val response = callClaude(MATCH_SYSTEM_PROMPT, userMessage)
        return responseJson.decodeFromString<MatchResult>(extractJson(response))
    }

    private suspend fun callClaude(systemPrompt: String, userMessage: String): String {
        val request = ClaudeRequest(
            model = MODEL,
            maxTokens = MAX_TOKENS,
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

    private fun buildEncourageMessage(
        headlineTitle: String,
        locale: String,
        articleText: String?,
        recentFigures: List<String> = emptyList()
    ): String = buildString {
        appendLine("## Headline")
        appendLine(headlineTitle)
        if (articleText != null) {
            appendLine()
            appendLine("## Article Text")
            appendLine(articleText)
        }
        appendLine()
        appendLine("## Response Language")
        appendLine(locale)
        if (recentFigures.isNotEmpty()) {
            appendLine()
            appendLine("## Figure Diversity")
            appendLine(
                "Vary the figures you select. Please avoid these recently used figures if a " +
                    "suitable alternative exists: ${recentFigures.joinToString(", ")}"
            )
        }
    }

    private fun buildMatchMessage(
        headlineTitle: String,
        candidateQuotes: List<QuoteCandidate>
    ): String = buildString {
        appendLine("## Headline")
        appendLine(headlineTitle)
        appendLine()
        appendLine("## Candidate Quotes")
        candidateQuotes.forEach { quote ->
            appendLine("- ID: ${quote.id}")
            appendLine("  Figure: ${quote.figureName}")
            appendLine("  Text: \"${quote.text}\"")
            appendLine("  Source: ${quote.source}")
            appendLine("  Themes: ${quote.themes.joinToString(", ")}")
            appendLine()
        }
    }

    private fun extractJson(text: String): String {
        val jsonBlockRegex = Regex("```json?\\s*\\n?(.*?)\\n?```", RegexOption.DOT_MATCHES_ALL)
        val match = jsonBlockRegex.find(text)
        return match?.groupValues?.get(1)?.trim() ?: text.trim()
    }
}

// ---- Supporting types ----

data class QuoteCandidate(
    val id: Long,
    val figureName: String,
    val text: String,
    val source: String,
    val themes: List<String>
)

@kotlinx.serialization.Serializable
data class EncourageResult(
    val summary: String? = null,
    val quoteText: String,
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

@kotlinx.serialization.Serializable
enum class EncourageTone {
    COMFORT,
    EXHORTATION,
    CORRECTION
}

@kotlinx.serialization.Serializable
data class MatchResult(
    val selectedQuoteId: Long,
    val confidence: Float,
    val explanation: String,
    val connectionThemes: List<String>
)

class ClaudeApiException(
    val statusCode: Int,
    override val message: String
) : RuntimeException(message)

// ---- System Prompts ----

private val ENCOURAGE_SYSTEM_PROMPT = """
You are a theological advisor for The Media Sage app. Given a news headline (and optionally the full article text), your role is to come alongside the reader with wisdom from the Christian tradition — in the spirit of parakaleo (Greek: to come alongside, encourage, exhort, comfort).

Discern which tone best fits the headline:
- COMFORT — for headlines about suffering, loss, disaster, or grief. Offer solace, hope, and the assurance of God's presence.
- EXHORTATION — for headlines about opportunity, community, or faithfulness. Call the reader to action, gratitude, or deeper engagement.
- CORRECTION — for headlines about moral drift, corruption, complacency, or injustice. Speak truth with love, as the prophets and apostles did — warning and calling people back to God's ways.

You must:
1. Discern the appropriate tone (COMFORT, EXHORTATION, or CORRECTION)
2. If article text is provided, write a brief summary (2-3 sentences) capturing the main point — like a journalist's lede. If no article text, set summary to null.
3. Select a real, verified quote from a professing Christian figure ONLY — theologians, mystics, prophets, apostles, pastors, missionaries, church fathers, or reformers. The figure MUST be someone who professed and practiced the Christian faith (no philosophers or thinkers outside the faith). Only select figures who lived before 1980. Do NOT fabricate quotes.
4. Identify a relevant scripture passage — scriptureReference is the citation (e.g. "Romans 8:28"), scriptureText is the FULL quoted verse text. These are two separate fields and both are REQUIRED.
5. Explain the connection in 2-3 sentences

Guidelines:
- Never trivialize suffering, and genuinely celebrate good news
- For CORRECTION tone: speak truth firmly but with love — the goal is restoration, not condemnation
- The figure's role should be a short descriptor (e.g., "Theologian & Martyr", "Bishop & Church Father", "Reformer")
- The scripture reference should be a specific verse or short passage
- connectionThemes should be 2-4 thematic connections
- matchTheme should be a 2-3 word label summarizing the connection

Respond in the language specified by the Response Language field (default: English).

Respond ONLY with valid JSON in this exact format:
{
  "summary": "<2-3 sentence article summary, or null if no article text provided>",
  "quoteText": "<the quote>",
  "figureName": "<full name of the figure>",
  "figureRole": "<short role descriptor>",
  "scriptureReference": "<e.g. Romans 8:28>",
  "scriptureText": "<the full verse text, e.g. 'And we know that in all things God works for the good of those who love him, who have been called according to his purpose.'>",
  "explanation": "<2-3 sentence explanation>",
  "connectionThemes": ["theme1", "theme2"],
  "matchTheme": "<2-3 word theme label>",
  "tone": "<COMFORT or EXHORTATION or CORRECTION>"
}
""".trimIndent()

private val ENCOURAGE_STREAM_SYSTEM_PROMPT = """
You are a theological advisor for The Media Sage app. Given a news headline (and optionally the full article text), your role is to come alongside the reader with wisdom from the Christian tradition — in the spirit of parakaleo (Greek: to come alongside, encourage, exhort, comfort).

Discern which tone best fits the headline:
- COMFORT — for headlines about suffering, loss, disaster, or grief
- EXHORTATION — for headlines about opportunity, community, or faithfulness
- CORRECTION — for headlines about moral drift, corruption, complacency, or injustice

You must:
1. Select a real, verified quote from a professing Christian figure ONLY — theologians, mystics, prophets, apostles, pastors, missionaries, church fathers, or reformers. Only figures who lived before 1980. Do NOT fabricate quotes.
2. Identify a relevant scripture passage — reference and full verse text are both required.
3. Explain the connection in 2-3 sentences.

Output your response in this EXACT format — field values only, separated by ---FIELD--- with no other text, labels, or punctuation between fields:

matchTheme---FIELD---tone---FIELD---summary---FIELD---quoteText---FIELD---figureName---FIELD---figureRole---FIELD---scriptureReference---FIELD---scriptureText---FIELD---explanation---FIELD---connectionThemes

Field definitions:
- matchTheme: 2-3 word theme label (e.g. "hope in darkness")
- tone: one of COMFORT, EXHORTATION, or CORRECTION
- summary: 2-3 sentence article summary if article text provided, otherwise empty string
- quoteText: the verified quote text
- figureName: full name (e.g. "Dietrich Bonhoeffer")
- figureRole: short descriptor (e.g. "Theologian & Martyr")
- scriptureReference: citation (e.g. "Romans 8:28")
- scriptureText: the full verse text
- explanation: 2-3 sentences connecting the headline, quote, and scripture
- connectionThemes: 2-4 themes as comma-separated values (e.g. "hope,perseverance,faith")

IMPORTANT: Output ONLY the ten field values separated by ---FIELD---. No labels, no JSON, no extra text.
""".trimIndent()

@Deprecated("Use ENCOURAGE_SYSTEM_PROMPT instead — TODO MS-46")
private val MATCH_SYSTEM_PROMPT = """
You are a theological advisor for the Media Sage app. Your role is to match news headlines with meaningful quotes from Christian theologians, mystics, and biblical figures.

You handle both troubling AND positive headlines:
- For troubling news (conflict, disaster, injustice): match with quotes offering comfort, hope, perseverance, or divine sovereignty
- For positive news (peace, breakthroughs, reconciliation): match with quotes celebrating peace, gratitude, God's faithfulness, or redemption

Guidelines:
- Acknowledge the reality of the news — never trivialize suffering, and genuinely celebrate good news
- Select the quote that most meaningfully speaks to the themes in the headline
- Explain the connection between the headline and the quote in 2-3 sentences
- Identify 2-4 connecting themes (e.g., "hope in suffering", "peacemaking", "divine sovereignty", "gratitude")
- Rate your confidence from 0.0 to 1.0 based on how strong the thematic connection is

Respond ONLY with valid JSON in this exact format:
{
  "selectedQuoteId": <id of the best matching quote>,
  "confidence": <0.0 to 1.0>,
  "explanation": "<2-3 sentence explanation>",
  "connectionThemes": ["theme1", "theme2"]
}
""".trimIndent()
