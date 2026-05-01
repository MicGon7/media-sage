package com.mediasage.server.service

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
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
        private const val QUOTE_GEN_MAX_TOKENS = 1024
        private const val RECENT_FIGURES_MAX = 10

        private val responseJson = Json {
            ignoreUnknownKeys = true
            isLenient = true
        }
    }

    suspend fun encourageHeadline(
        headlineTitle: String,
        locale: String = "en",
        articleText: String? = null
    ): EncourageResult {
        val excluded = synchronized(recentFigures) { recentFigures.keys.toList() }
        val userMessage = buildEncourageMessage(headlineTitle, locale, articleText, excluded)
        val response = callClaude(ENCOURAGE_SYSTEM_PROMPT, userMessage)
        val result = responseJson.decodeFromString<EncourageResult>(extractJson(response))
        synchronized(recentFigures) { recentFigures[result.figureName] = Unit }
        return result
    }

    suspend fun generateQuotesForFigure(
        name: String,
        role: String,
        category: String,
        century: String,
        lifespan: String
    ): List<GeneratedQuote> {
        val userMessage = buildQuoteGenerationMessage(name, role, category, century, lifespan)
        val response = callClaude(QUOTE_GENERATION_SYSTEM_PROMPT, userMessage, QUOTE_GEN_MAX_TOKENS)
        return responseJson.decodeFromString<GeneratedQuotesResult>(extractJson(response)).quotes
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

    private fun buildQuoteGenerationMessage(
        name: String,
        role: String,
        category: String,
        century: String,
        lifespan: String
    ): String = buildString {
        appendLine("## Figure")
        appendLine("Name: $name")
        appendLine("Role: $role")
        appendLine("Category: $category")
        appendLine("Century: $century")
        appendLine("Lifespan: $lifespan")
    }

    private fun buildEncourageMessage(
        headlineTitle: String,
        locale: String,
        articleText: String?,
        recentFigures: List<String>
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

@Serializable
data class GeneratedQuote(
    val text: String,
    val source: String,
    val themes: List<String>
)

@Serializable
data class GeneratedQuotesResult(
    val quotes: List<GeneratedQuote>
)

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

private val QUOTE_GENERATION_SYSTEM_PROMPT = """
You are a theological research assistant for The Media Sage app. Given a Christian historical figure, return up to 5 verbatim quotes from their documented writings, sermons, or letters.

Rules:
- ONLY include quotes that are verbatim (or near-verbatim translations) from the figure's documented writings, sermons, or letters
- NEVER paraphrase, summarize, or invent quotes — if you cannot find a real verifiable quote, skip it
- Quality over quantity: 3 real quotes beats 5 fabricated ones — return fewer if necessary
- Each source must be a real, specific work: book title, sermon name, letter recipient, etc. — no "spirit of" or generic attributions
- Each quote must have 3–5 theme tags (single words or short phrases, lowercase, e.g. "grace", "suffering", "prayer", "justice")
- Vary themes across the quotes to cover the breadth of the figure's documented thought
- Quotes should range from short (one sentence) to medium length (2–3 sentences)

Respond ONLY with valid JSON in this exact format:
{
  "quotes": [
    {
      "text": "<verbatim quote text>",
      "source": "<specific book, sermon, or letter title>",
      "themes": ["theme1", "theme2", "theme3"]
    }
  ]
}
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
