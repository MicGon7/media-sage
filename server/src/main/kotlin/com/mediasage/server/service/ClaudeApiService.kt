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

        private val responseJson = Json {
            ignoreUnknownKeys = true
            isLenient = true
        }
    }

    suspend fun matchQuoteToHeadline(
        headlineTitle: String,
        candidateQuotes: List<QuoteCandidate>
    ): MatchResult {
        // Build the user message with headline and candidate quotes
        val userMessage = buildUserMessage(headlineTitle, candidateQuotes)

        // Make the API call
        val response = callClaude(SYSTEM_PROMPT, userMessage)

        // Parse Claude's JSON response into our domain result
        return parseMatchResponse(response)
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

        // Handle error responses
        if (!httpResponse.status.isSuccess()) {
            val errorBody = httpResponse.bodyAsText()
            throw ClaudeApiException(
                statusCode = httpResponse.status.value,
                message = "Claude API error (${httpResponse.status}): $errorBody"
            )
        }

        // Extract the text from the response
        val claudeResponse = httpResponse.body<ClaudeResponse>()
        return claudeResponse.content.firstOrNull()?.text
            ?: throw ClaudeApiException(500, "Empty response from Claude")
    }

    private fun buildUserMessage(
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

    private fun parseMatchResponse(responseText: String): MatchResult {
        // Claude returns JSON in its text response — parse it
        // We extract the JSON block in case Claude wraps it in markdown
        val jsonString = extractJson(responseText)
        return responseJson.decodeFromString<MatchResult>(jsonString)
    }

    private fun extractJson(text: String): String {
        // Claude sometimes wraps JSON in ```json ... ``` markdown blocks
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

// ---- System Prompt ----

private val SYSTEM_PROMPT = """
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
