package com.mediasage.advisor.tools

import com.mediasage.pipeline.core.JobsTable
import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.types.CallToolRequest
import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import io.modelcontextprotocol.kotlin.sdk.types.ToolSchema
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import java.math.BigDecimal
import java.util.UUID

internal fun Server.registerCompareRunsTool() {
    addTool(
        name = "compare_runs",
        description = "Compare two pipeline runs side-by-side — cost, turns, duration, status.",
        inputSchema = ToolSchema(
            properties = buildJsonObject {
                putJsonObject("job_id_a") {
                    put("type", "string")
                    put("description", "UUID of the first run")
                }
                putJsonObject("job_id_b") {
                    put("type", "string")
                    put("description", "UUID of the second run")
                }
            },
            required = listOf("job_id_a", "job_id_b"),
        ),
    ) { request -> handleCompare(request) }
}

private suspend fun handleCompare(request: CallToolRequest): CallToolResult {
    val idA = request.arguments?.get("job_id_a")?.jsonPrimitive?.content
    val idB = request.arguments?.get("job_id_b")?.jsonPrimitive?.content
    if (idA == null || idB == null) {
        val missing = if (idA == null) "job_id_a" else "job_id_b"
        return CallToolResult(content = listOf(TextContent(text = "$missing is required.")))
    }
    val uuidA = runCatching { UUID.fromString(idA) }.getOrNull()
    val uuidB = runCatching { UUID.fromString(idB) }.getOrNull()
    if (uuidA == null || uuidB == null) {
        val invalid = if (uuidA == null) idA else idB
        return CallToolResult(content = listOf(TextContent(text = "Invalid UUID: $invalid")))
    }
    val rowA = loadJobSummary(uuidA)
    val rowB = loadJobSummary(uuidB)
    if (rowA == null || rowB == null) {
        val missing = if (rowA == null) idA else idB
        return CallToolResult(content = listOf(TextContent(text = "Job not found: $missing")))
    }
    return CallToolResult(content = listOf(TextContent(text = formatComparison(rowA, rowB))))
}

internal data class JobSummary(
    val jobId: String,
    val ticketKey: String,
    val status: String,
    val totalCostUsd: BigDecimal?,
    val numTurns: Int?,
    val claudeDurationMs: Long?,
    val inputTokens: Int?,
    val outputTokens: Int?,
    val failedGate: String?,
)

private fun loadJobSummary(jobId: UUID): JobSummary? = transaction {
    JobsTable.selectAll().where { JobsTable.jobId eq jobId }.singleOrNull()?.let { row ->
        JobSummary(
            jobId = row[JobsTable.jobId].toString(),
            ticketKey = row[JobsTable.ticketKey],
            status = row[JobsTable.status],
            totalCostUsd = row[JobsTable.totalCostUsd],
            numTurns = row[JobsTable.numTurns],
            claudeDurationMs = row[JobsTable.claudeDurationMs],
            inputTokens = row[JobsTable.inputTokens],
            outputTokens = row[JobsTable.outputTokens],
            failedGate = row[JobsTable.failedGate],
        )
    }
}

internal fun formatComparison(a: JobSummary, b: JobSummary): String = buildString {
    appendLine("%-20s  %-38s  %-38s".format("Field", "Run A", "Run B"))
    appendLine("-".repeat(100))
    appendLine("%-20s  %-38s  %-38s".format("job_id", a.jobId.take(36), b.jobId.take(36)))
    appendLine("%-20s  %-38s  %-38s".format("ticket", a.ticketKey, b.ticketKey))
    appendLine("%-20s  %-38s  %-38s".format("status", a.status, b.status))
    appendLine("%-20s  %-38s  %-38s".format("cost_usd",
        a.totalCostUsd?.toPlainString() ?: "-", b.totalCostUsd?.toPlainString() ?: "-"))
    appendLine("%-20s  %-38s  %-38s".format("turns",
        a.numTurns?.toString() ?: "-", b.numTurns?.toString() ?: "-"))
    appendLine("%-20s  %-38s  %-38s".format("duration_ms",
        a.claudeDurationMs?.toString() ?: "-", b.claudeDurationMs?.toString() ?: "-"))
    appendLine("%-20s  %-38s  %-38s".format("input_tokens",
        a.inputTokens?.toString() ?: "-", b.inputTokens?.toString() ?: "-"))
    appendLine("%-20s  %-38s  %-38s".format("output_tokens",
        a.outputTokens?.toString() ?: "-", b.outputTokens?.toString() ?: "-"))
    appendLine("%-20s  %-38s  %-38s".format("failed_gate",
        a.failedGate ?: "-", b.failedGate ?: "-"))
}
