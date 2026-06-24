package com.mediasage.analyst.scoring

import com.mediasage.pipeline.core.DecisionScoresTable
import com.mediasage.pipeline.core.TranscriptsTable
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.slf4j.LoggerFactory
import java.util.UUID

private val log = LoggerFactory.getLogger(ClaudeDecisionScorer::class.java)

private const val CLAUDE_API_URL = "https://api.anthropic.com/v1/messages"
private const val CLAUDE_API_VERSION = "2023-06-01"
private const val CLAUDE_MODEL = "claude-sonnet-4-6"
private const val MAX_TOKENS = 1024

private val responseJson = Json { ignoreUnknownKeys = true }

/**
 * Scores a completed worker session against the decision-scoring rubric using Claude as a judge.
 *
 * For each job: reads the transcript from `transcripts`, sends it to Claude with the rubric,
 * parses the structured response, and persists one row per criterion to `decision_scores`.
 * Uses `decision_index = 0` for overall session-level scores.
 *
 * Scoring is fire-and-forget from the Pub/Sub handler — any failure is logged and swallowed
 * so the Analyst never disrupts Pub/Sub acknowledgement.
 */
class ClaudeDecisionScorer(
    private val httpClient: HttpClient,
    private val apiKey: String,
) : DecisionScorer {

    private val rubric: String by lazy {
        ClaudeDecisionScorer::class.java
            .getResourceAsStream("/rubrics/decision-scoring.md")
            ?.bufferedReader()
            ?.readText()
            ?: error("Rubric file not found at /rubrics/decision-scoring.md")
    }

    override suspend fun score(jobId: UUID) {
        val transcript = readTranscript(jobId)
        if (transcript == null) {
            log.info("[{}] no transcript found — skipping decision scoring", jobId)
            return
        }
        val scores = runCatching { callClaude(transcript) }.getOrElse { e ->
            log.warn("[{}] Claude scoring failed: {}", jobId, e.message)
            return
        }
        persistScores(jobId, scores)
        log.info("[{}] decision scoring complete — {} criteria scored", jobId, scores.size)
    }

    private suspend fun readTranscript(jobId: UUID): String? = withContext(Dispatchers.IO) {
        transaction {
            TranscriptsTable.selectAll()
                .where { TranscriptsTable.jobId eq jobId }
                .map { it[TranscriptsTable.content] }
                .firstOrNull()
        }
    }

    private suspend fun callClaude(transcript: String): List<DecisionScoreResult> {
        val userMessage = buildUserMessage(transcript)
        val request = ClaudeRequest(
            model = CLAUDE_MODEL,
            maxTokens = MAX_TOKENS,
            system = SYSTEM_PROMPT,
            messages = listOf(ClaudeMessage(role = "user", content = userMessage)),
        )
        val httpResponse = httpClient.post(CLAUDE_API_URL) {
            contentType(ContentType.Application.Json)
            header("x-api-key", apiKey)
            header("anthropic-version", CLAUDE_API_VERSION)
            setBody(request)
        }
        if (!httpResponse.status.isSuccess()) {
            val body = httpResponse.bodyAsText()
            error("Claude API error (${httpResponse.status}): $body")
        }
        val claudeResponse = httpResponse.body<ClaudeResponse>()
        val text = claudeResponse.content.firstOrNull()?.text
            ?: error("Empty response from Claude")
        return responseJson.decodeFromString<ScoringResponse>(extractJson(text)).scores
    }

    private suspend fun persistScores(jobId: UUID, scores: List<DecisionScoreResult>) =
        withContext(Dispatchers.IO) {
            transaction {
                scores.forEach { score ->
                    DecisionScoresTable.insert {
                        it[DecisionScoresTable.jobId] = jobId
                        it[decisionIndex] = 0
                        it[criterion] = score.criterion
                        it[DecisionScoresTable.score] = score.score.coerceIn(1, 5)
                        it[rationale] = score.rationale
                    }
                }
            }
        }

    private fun buildUserMessage(transcript: String) = """
        Here is the rubric:

        $rubric

        Here is the worker session transcript (Claude Code JSONL):

        $transcript

        Score the session against each rubric criterion. Return a JSON object with this exact shape — no markdown, no extra keys:
        {"scores":[{"criterion":"tool_choice","score":4,"rationale":"one sentence"},{"criterion":"retry_recovery","score":3,"rationale":"one sentence"},{"criterion":"context_management","score":5,"rationale":"one sentence"}]}
    """.trimIndent()

    private fun extractJson(text: String): String {
        val jsonBlock = Regex("```json?\\s*\\n?(.*?)\\n?```", RegexOption.DOT_MATCHES_ALL).find(text)
        return jsonBlock?.groupValues?.get(1)?.trim() ?: text.trim()
    }

    companion object {
        private const val SYSTEM_PROMPT =
            "You are a code-review judge evaluating AI agent sessions. " +
                "Be concise and objective. Return only valid JSON — no explanation outside the JSON."
    }
}

// ---- Request / response types ----

@Serializable
private data class ClaudeRequest(
    val model: String,
    @SerialName("max_tokens") val maxTokens: Int,
    val system: String,
    val messages: List<ClaudeMessage>,
)

@Serializable
private data class ClaudeMessage(val role: String, val content: String)

@Serializable
private data class ClaudeResponse(val content: List<ContentBlock>)

@Serializable
private data class ContentBlock(val type: String = "", val text: String = "")

@Serializable
private data class ScoringResponse(val scores: List<DecisionScoreResult>)

@Serializable
data class DecisionScoreResult(
    val criterion: String,
    val score: Int,
    val rationale: String,
)
