package com.mediasage.advisor.tools

import com.mediasage.pipeline.core.TranscriptsTable
import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import io.modelcontextprotocol.kotlin.sdk.types.ToolSchema
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import java.util.UUID

internal fun Server.registerFetchTranscriptTool() {
    addTool(
        name = "fetch_transcript",
        description = "Fetch the raw JSONL transcript for a specific pipeline run by job ID.",
        inputSchema = ToolSchema(
            properties = buildJsonObject {
                putJsonObject("job_id") {
                    put("type", "string")
                    put("description", "UUID of the job whose transcript to fetch")
                }
            },
            required = listOf("job_id"),
        ),
    ) { request ->
        val jobIdStr = request.arguments?.get("job_id")?.jsonPrimitive?.content
            ?: return@addTool CallToolResult(content = listOf(TextContent(text = "job_id is required.")))
        val jobId = runCatching { UUID.fromString(jobIdStr) }.getOrNull()
            ?: return@addTool CallToolResult(content = listOf(TextContent(text = "Invalid UUID: $jobIdStr")))
        val content = fetchTranscript(jobId)
            ?: return@addTool CallToolResult(content = listOf(TextContent(text = "No transcript found for $jobIdStr")))
        CallToolResult(content = listOf(TextContent(text = content)))
    }
}

private fun fetchTranscript(jobId: UUID): String? = transaction {
    TranscriptsTable
        .selectAll()
        .where { TranscriptsTable.jobId eq jobId }
        .singleOrNull()
        ?.get(TranscriptsTable.content)
}
