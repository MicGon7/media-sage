package com.mediasage.advisor.tools

import com.mediasage.advisor.AnthropicApi
import com.mediasage.advisor.ClaudeMessage
import com.mediasage.advisor.ClaudeRequest
import com.mediasage.advisor.callClaudeWithRetry
import com.mediasage.pipeline.core.DecisionScoresTable
import com.mediasage.pipeline.core.TranscriptsTable
import io.ktor.client.HttpClient
import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import io.modelcontextprotocol.kotlin.sdk.types.ToolSchema
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import java.util.UUID

private const val ANALYZE_TOOL_NAME = "record_analysis"
private const val MODEL = "claude-sonnet-4-6"

private val ANALYZE_TOOL = buildJsonObject {
    put("name", ANALYZE_TOOL_NAME)
    put("description", "Record session efficiency analysis")
    putJsonObject("input_schema") {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("total_turns") { put("type", "integer") }
            putJsonObject("discovery_turns") { put("type", "integer") }
            putJsonObject("wasted_turn_count") { put("type", "integer") }
            putJsonObject("wasted_turn_causes") {
                put("type", "array")
                putJsonObject("items") { put("type", "string") }
            }
            putJsonObject("recommendation") { put("type", "string") }
        }
        put("required", kotlinx.serialization.json.buildJsonArray {
            add(JsonPrimitive("total_turns"))
            add(JsonPrimitive("recommendation"))
        })
    }
}

private val TOOL_CHOICE = buildJsonObject {
    put("type", "tool")
    put("name", ANALYZE_TOOL_NAME)
}

private val SYSTEM_PROMPT = """
You are analyzing a Claude Code worker session transcript (JSONL format).
Count the agentic turns (assistant → tool_use → tool_result cycles) and identify
patterns of inefficiency like repeated file reads, redundant searches, or backtracking.
Rubric scores from a prior evaluation are provided above the transcript — factor them
into your analysis and recommendation.
Report via the $ANALYZE_TOOL_NAME tool.
""".trimIndent()

internal fun Server.registerAnalyzeRunTool(client: HttpClient, baseUrl: String, authToken: String) {
    addTool(
        name = "analyze_run",
        description = "Use Claude to analyze a pipeline run transcript for turn efficiency and waste.",
        inputSchema = ToolSchema(
            properties = buildJsonObject {
                putJsonObject("job_id") {
                    put("type", "string")
                    put("description", "UUID of the job to analyze")
                }
            },
            required = listOf("job_id"),
        ),
    ) { request ->
        val jobIdStr = request.arguments?.get("job_id")?.jsonPrimitive?.content
            ?: return@addTool CallToolResult(content = listOf(TextContent(text = "job_id is required.")))
        val jobId = runCatching { UUID.fromString(jobIdStr) }.getOrNull()
            ?: return@addTool CallToolResult(content = listOf(TextContent(text = "Invalid UUID: $jobIdStr")))
        val transcript = loadTranscript(jobId)
            ?: return@addTool CallToolResult(content = listOf(TextContent(text = "No transcript for $jobIdStr")))
        val scores = loadDecisionScores(jobId)
        val analysis = runAnalysis(client, baseUrl, authToken, transcript, scores)
        CallToolResult(content = listOf(TextContent(text = analysis)))
    }
}

private data class DecisionScore(
    val criterion: String,
    val score: Int,
    val rationale: String,
    val recommendation: String,
)

private fun loadTranscript(jobId: UUID): String? = transaction {
    TranscriptsTable.selectAll()
        .where { TranscriptsTable.jobId eq jobId }
        .singleOrNull()
        ?.get(TranscriptsTable.content)
}

private fun loadDecisionScores(jobId: UUID): List<DecisionScore> = transaction {
    DecisionScoresTable.selectAll()
        .where { DecisionScoresTable.jobId eq jobId }
        .orderBy(DecisionScoresTable.criterion)
        .map { row ->
            DecisionScore(
                criterion = row[DecisionScoresTable.criterion],
                score = row[DecisionScoresTable.score],
                rationale = row[DecisionScoresTable.rationale],
                recommendation = row[DecisionScoresTable.recommendation],
            )
        }
}

private suspend fun runAnalysis(
    client: HttpClient,
    baseUrl: String,
    authToken: String,
    transcript: String,
    scores: List<DecisionScore>,
): String {
    val context = buildString {
        if (scores.isNotEmpty()) {
            appendLine("## Rubric Scores")
            scores.forEach { s ->
                appendLine("${s.criterion}: ${s.score}/5 — ${s.rationale} | Fix: ${s.recommendation}")
            }
            appendLine()
        }
        appendLine("## Session Transcript")
        append(transcript)
    }
    val claudeRequest = ClaudeRequest(
        model = MODEL,
        maxTokens = AnthropicApi.TokenBudget.STANDARD,
        system = SYSTEM_PROMPT,
        messages = listOf(ClaudeMessage("user", context)),
        tools = listOf(ANALYZE_TOOL),
        toolChoice = TOOL_CHOICE,
    )
    val result = callClaudeWithRetry(client, baseUrl, authToken, claudeRequest)
        ?: return "Analysis failed after retries."
    return formatAnalysis(result.jsonObject)
}

private fun formatAnalysis(data: JsonObject): String = buildString {
    appendLine("## Session Efficiency Analysis")
    appendLine("Total turns    : ${data["total_turns"]?.jsonPrimitive?.content ?: "?"}")
    appendLine("Discovery turns: ${data["discovery_turns"]?.jsonPrimitive?.content ?: "?"}")
    appendLine("Wasted turns   : ${data["wasted_turn_count"]?.jsonPrimitive?.content ?: "?"}")
    appendLine("Recommendation : ${data["recommendation"]?.jsonPrimitive?.content ?: "-"}")
}
