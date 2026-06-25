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
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.slf4j.LoggerFactory
import java.util.UUID

private val log = LoggerFactory.getLogger(ClaudeDecisionScorer::class.java)

private const val ANTHROPIC_API_DEFAULT_BASE_URL = "https://api.anthropic.com"
private const val CLAUDE_API_VERSION = "2023-06-01"
private const val CLAUDE_MODEL = "claude-sonnet-4-6"
private const val MAX_TOKENS = 2048
private const val MAX_ATTEMPTS = 3
private val RETRY_DELAYS_MS = listOf(1_000L, 2_000L)

private val responseJson = Json { ignoreUnknownKeys = true }

private val SCORING_SCHEMA: JsonObject = buildJsonObject {
    put("type", "object")
    putJsonObject("properties") {
        putJsonObject("scores") {
            put("type", "array")
            putJsonObject("items") {
                put("type", "object")
                putJsonObject("properties") {
                    putJsonObject("criterion") { put("type", "string") }
                    putJsonObject("score") {
                        put("type", "integer")
                        put("minimum", 1)
                        put("maximum", 5)
                    }
                    putJsonObject("rationale") { put("type", "string") }
                    putJsonObject("recommendation") { put("type", "string") }
                }
                putJsonArray("required") {
                    add("criterion")
                    add("score")
                    add("rationale")
                    add("recommendation")
                }
            }
        }
    }
    putJsonArray("required") { add("scores") }
}

// Tool calling forces structured JSON output — more reliable than output_config which the
// Fuelix proxy silently ignores, causing Claude to fall back to unstructured markdown.
private val SCORING_TOOL: JsonObject = buildJsonObject {
    put("name", "record_scores")
    put("description", "Record the rubric scores for this worker session")
    put("input_schema", SCORING_SCHEMA)
}

private val TOOL_CHOICE: JsonObject = buildJsonObject {
    put("type", "tool")
    put("name", "record_scores")
}

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
    private val authToken: String,
    baseUrl: String = ANTHROPIC_API_DEFAULT_BASE_URL,
) : DecisionScorer {

    private val messagesUrl = "${baseUrl.trimEnd('/')}/v1/messages"

    private val rubric: String by lazy {
        ClaudeDecisionScorer::class.java
            .getResourceAsStream("/rubrics/decision-scoring.md")
            ?.bufferedReader()
            ?.readText()
            ?: error("Rubric file not found at /rubrics/decision-scoring.md")
    }

    override suspend fun score(jobId: UUID) {
        val transcript = readTranscript(jobId) ?: run {
            log.info("[{}] no transcript found — skipping decision scoring", jobId)
            return
        }
        runCatching { callClaudeWithRetry(transcript) }
            .onSuccess { scores ->
                persistScores(jobId, scores)
                log.info("[{}] decision scoring complete — {} criteria scored", jobId, scores.size)
            }
            .onFailure { e ->
                log.error(
                    "[{}] Claude scoring failed after {} attempts — last error: {}",
                    jobId, MAX_ATTEMPTS, e.message,
                )
            }
    }

    internal suspend fun callClaudeWithRetry(transcript: String): List<DecisionScoreResult> {
        var lastError: Throwable? = null
        for (attempt in 1..MAX_ATTEMPTS) {
            if (attempt > 1) delay(RETRY_DELAYS_MS[attempt - 2])
            runCatching { callClaude(transcript) }
                .onSuccess { return it }
                .onFailure { e ->
                    lastError = e
                    log.warn("Claude scoring attempt {}/{} failed: {}", attempt, MAX_ATTEMPTS, e.message)
                }
        }
        throw lastError!!
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
        val request = ClaudeRequest(
            model = CLAUDE_MODEL,
            maxTokens = MAX_TOKENS,
            system = SYSTEM_PROMPT,
            messages = listOf(ClaudeMessage(role = "user", content = buildUserMessage(transcript))),
            tools = listOf(SCORING_TOOL),
            toolChoice = TOOL_CHOICE,
        )
        val httpResponse = httpClient.post(messagesUrl) {
            contentType(ContentType.Application.Json)
            header("Authorization", "Bearer $authToken")
            header("anthropic-version", CLAUDE_API_VERSION)
            setBody(request)
        }
        if (!httpResponse.status.isSuccess()) {
            val body = httpResponse.bodyAsText()
            error("Claude API error (${httpResponse.status}): $body")
        }
        val claudeResponse = httpResponse.body<ClaudeResponse>()
        val toolUseBlock = claudeResponse.content.firstOrNull { it.type == "tool_use" }
            ?: error("No tool_use block in Claude response")
        val input = toolUseBlock.input
            ?: error("No input in tool_use block")
        return responseJson.decodeFromString<ScoringResponse>(input.toString()).scores
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
                        it[recommendation] = score.recommendation
                    }
                }
            }
        }

    private fun buildUserMessage(transcript: String) = """
        Here is the rubric:

        $rubric

        Here is the worker session transcript (Claude Code JSONL):

        $transcript

        Score the session against each rubric criterion.
    """.trimIndent()

    companion object {
        private const val SYSTEM_PROMPT =
            "You are a code-review judge evaluating AI agent sessions. Be concise and objective."
    }
}

// ---- Request / response types ----

@Serializable
private data class ClaudeRequest(
    val model: String,
    @SerialName("max_tokens") val maxTokens: Int,
    val system: String,
    val messages: List<ClaudeMessage>,
    val tools: List<JsonObject>,
    @SerialName("tool_choice") val toolChoice: JsonObject,
)

@Serializable
private data class ClaudeMessage(val role: String, val content: String)

@Serializable
private data class ClaudeResponse(val content: List<ContentBlock>)

@Serializable
private data class ContentBlock(
    val type: String = "",
    val text: String = "",
    val input: JsonObject? = null,
)

@Serializable
private data class ScoringResponse(val scores: List<DecisionScoreResult>)

@Serializable
data class DecisionScoreResult(
    val criterion: String,
    val score: Int,
    val rationale: String,
    val recommendation: String,
)
