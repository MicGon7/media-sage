package com.mediasage.advisor.tools

import com.mediasage.advisor.AnthropicApi
import com.mediasage.advisor.ClaudeMessage
import com.mediasage.advisor.ClaudeRequest
import com.mediasage.advisor.PropertySchema
import com.mediasage.advisor.ToolChoice
import com.mediasage.advisor.ToolDefinition
import com.mediasage.advisor.ToolInputSchema
import com.mediasage.advisor.callClaudeWithRetry
import com.mediasage.pipeline.core.JobsTable
import com.mediasage.pipeline.core.TranscriptsTable
import io.ktor.client.HttpClient
import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import io.modelcontextprotocol.kotlin.sdk.types.ToolSchema
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import java.util.UUID

private const val EXPLAIN_TOOL_NAME = "record_explanation"

private val EXPLAIN_TOOL = ToolDefinition(
    name = EXPLAIN_TOOL_NAME,
    description = "Record failure root cause and proposed fix",
    inputSchema = ToolInputSchema(
        properties = mapOf(
            "root_cause" to PropertySchema(type = "string"),
            "proposed_fix" to PropertySchema(type = "string"),
            "confidence" to PropertySchema(type = "string", description = "high / medium / low"),
        ),
        required = listOf("root_cause", "proposed_fix"),
    ),
)

private val TOOL_CHOICE = ToolChoice(type = "tool", name = EXPLAIN_TOOL_NAME)

internal fun Server.registerExplainFailureTool(client: HttpClient, baseUrl: String, authToken: String, model: String) {
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
        val explanation = runExplanation(client, baseUrl, authToken, model, context)
        CallToolResult(content = listOf(TextContent(text = explanation)))
    }
}

private data class FailureContext(val ticketKey: String, val transcript: String)

private fun loadFailureContext(jobId: UUID): FailureContext? = transaction {
    val job = JobsTable.selectAll().where { JobsTable.jobId eq jobId }.singleOrNull()
        ?: return@transaction null
    val transcript = TranscriptsTable.selectAll()
        .where { TranscriptsTable.jobId eq jobId }
        .singleOrNull()
        ?.get(TranscriptsTable.content) ?: return@transaction null
    FailureContext(
        ticketKey = job[JobsTable.ticketKey],
        transcript = transcript,
    )
}

private suspend fun runExplanation(
    client: HttpClient,
    baseUrl: String,
    authToken: String,
    model: String,
    context: FailureContext,
): String {
    val system = buildString {
        appendLine("You are diagnosing a failed Claude Code worker session.")
        appendLine("Ticket: ${context.ticketKey}")
        appendLine("Review the transcript to identify root cause and propose a concrete fix.")
        appendLine("Report via the $EXPLAIN_TOOL_NAME tool.")
    }
    val claudeRequest = ClaudeRequest(
        model = model,
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
    appendLine("Root cause  : ${data["root_cause"]?.jsonPrimitive?.content ?: "-"}")
    appendLine("Proposed fix: ${data["proposed_fix"]?.jsonPrimitive?.content ?: "-"}")
    appendLine("Confidence  : ${data["confidence"]?.jsonPrimitive?.content ?: "-"}")
}
