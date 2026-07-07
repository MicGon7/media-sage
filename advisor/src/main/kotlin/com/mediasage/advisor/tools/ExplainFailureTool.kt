package com.mediasage.advisor.tools

import com.mediasage.advisor.AnthropicApi
import com.mediasage.advisor.ClaudeMessage
import com.mediasage.advisor.ClaudeRequest
import com.mediasage.advisor.callClaudeWithRetry
import com.mediasage.pipeline.core.JobsTable
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

private const val EXPLAIN_TOOL_NAME = "record_explanation"
private const val MODEL = "claude-sonnet-4-6"

private val EXPLAIN_TOOL = buildJsonObject {
    put("name", EXPLAIN_TOOL_NAME)
    put("description", "Record failure root cause and proposed fix")
    putJsonObject("input_schema") {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("failed_gate") { put("type", "string") }
            putJsonObject("root_cause") { put("type", "string") }
            putJsonObject("proposed_fix") { put("type", "string") }
            putJsonObject("confidence") {
                put("type", "string")
                put("description", "high / medium / low")
            }
        }
        put("required", kotlinx.serialization.json.buildJsonArray {
            add(JsonPrimitive("root_cause"))
            add(JsonPrimitive("proposed_fix"))
        })
    }
}

private val TOOL_CHOICE = buildJsonObject {
    put("type", "tool")
    put("name", EXPLAIN_TOOL_NAME)
}

internal fun Server.registerExplainFailureTool(client: HttpClient, baseUrl: String, authToken: String) {
    addTool(
        name = "explain_failure",
        description = "Use Claude to diagnose why a pipeline run failed and propose a fix.",
        inputSchema = ToolSchema(
            properties = buildJsonObject {
                putJsonObject("job_id") {
                    put("type", "string")
                    put("description", "UUID of the failed job")
                }
            },
            required = listOf("job_id"),
        ),
    ) { request ->
        val jobIdStr = request.arguments?.get("job_id")?.jsonPrimitive?.content
            ?: return@addTool CallToolResult(content = listOf(TextContent(text = "job_id is required.")))
        val jobId = runCatching { UUID.fromString(jobIdStr) }.getOrNull()
            ?: return@addTool CallToolResult(content = listOf(TextContent(text = "Invalid UUID: $jobIdStr")))
        val context = loadFailureContext(jobId)
            ?: return@addTool CallToolResult(
                content = listOf(TextContent(text = "Job not found or no transcript for $jobIdStr")),
            )
        val explanation = runExplanation(client, baseUrl, authToken, context)
        CallToolResult(content = listOf(TextContent(text = explanation)))
    }
}

private data class FailureContext(val gate: String?, val ticketKey: String, val transcript: String)

private fun loadFailureContext(jobId: UUID): FailureContext? = transaction {
    val job = JobsTable.selectAll().where { JobsTable.jobId eq jobId }.singleOrNull()
        ?: return@transaction null
    val transcript = TranscriptsTable.selectAll()
        .where { TranscriptsTable.jobId eq jobId }
        .singleOrNull()
        ?.get(TranscriptsTable.content) ?: return@transaction null
    FailureContext(
        gate = job[JobsTable.failedGate],
        ticketKey = job[JobsTable.ticketKey],
        transcript = transcript,
    )
}

private suspend fun runExplanation(
    client: HttpClient,
    baseUrl: String,
    authToken: String,
    context: FailureContext,
): String {
    val system = buildString {
        appendLine("You are diagnosing a failed Claude Code worker session.")
        if (context.gate != null) appendLine("The worker stopped because the '${context.gate}' quality gate failed.")
        appendLine("Ticket: ${context.ticketKey}")
        appendLine("Review the transcript to identify root cause and propose a concrete fix.")
        appendLine("Report via the $EXPLAIN_TOOL_NAME tool.")
    }
    val claudeRequest = ClaudeRequest(
        model = MODEL,
        maxTokens = AnthropicApi.TokenBudget.STANDARD,
        system = system,
        messages = listOf(ClaudeMessage("user", preprocessTranscript(context.transcript))),
        tools = listOf(EXPLAIN_TOOL),
        toolChoice = TOOL_CHOICE,
    )
    val result = callClaudeWithRetry(client, baseUrl, authToken, claudeRequest)
        ?: return "Explanation failed after retries."
    return formatExplanation(result.jsonObject)
}

private fun formatExplanation(data: JsonObject): String = buildString {
    appendLine("## Failure Analysis")
    appendLine("Gate        : ${data["failed_gate"]?.jsonPrimitive?.content ?: "unknown"}")
    appendLine("Root cause  : ${data["root_cause"]?.jsonPrimitive?.content ?: "-"}")
    appendLine("Proposed fix: ${data["proposed_fix"]?.jsonPrimitive?.content ?: "-"}")
    appendLine("Confidence  : ${data["confidence"]?.jsonPrimitive?.content ?: "-"}")
}
