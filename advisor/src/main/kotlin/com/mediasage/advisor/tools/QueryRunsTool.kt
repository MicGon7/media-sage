package com.mediasage.advisor.tools

import com.mediasage.pipeline.core.JobsTable
import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import io.modelcontextprotocol.kotlin.sdk.types.ToolSchema
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.andWhere
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction

private const val DEFAULT_LIMIT = 10

internal fun Server.registerQueryRunsTool() {
    addTool(
        name = "query_runs",
        description = "List recent pipeline runs. Optionally filter by ticket key or status " +
            "(PENDING, RUNNING, COMPLETED, FAILED, INTERRUPTED). Defaults to 10 most recent.",
        inputSchema = ToolSchema(
            properties = buildJsonObject {
                putJsonObject("ticket_key") {
                    put("type", "string")
                    put("description", "Filter by Jira ticket key, e.g. MS-123")
                }
                putJsonObject("status") {
                    put("type", "string")
                    put("description", "Filter by job status")
                }
                putJsonObject("limit") {
                    put("type", "integer")
                    put("description", "Max rows to return (default 10)")
                }
            },
        ),
    ) { request ->
        val ticketKey = request.arguments?.get("ticket_key")?.jsonPrimitive?.content
        val status = request.arguments?.get("status")?.jsonPrimitive?.content
        val limit = request.arguments?.get("limit")?.jsonPrimitive?.content?.toIntOrNull() ?: DEFAULT_LIMIT
        val rows = fetchRuns(ticketKey, status, limit)
        CallToolResult(content = listOf(TextContent(text = formatJobRows(rows))))
    }
}

internal data class JobRow(
    val jobId: String,
    val ticketKey: String,
    val status: String,
    val createdAt: String,
    val totalCostUsd: String,
    val numTurns: Int?,
)

private fun fetchRuns(ticketKey: String?, status: String?, limit: Int): List<JobRow> = transaction {
    var query = JobsTable.selectAll().orderBy(JobsTable.createdAt, SortOrder.DESC).limit(limit)
    if (ticketKey != null) query = query.andWhere { JobsTable.ticketKey eq ticketKey }
    if (status != null) query = query.andWhere { JobsTable.status eq status }
    query.map { row ->
        JobRow(
            jobId = row[JobsTable.jobId].toString(),
            ticketKey = row[JobsTable.ticketKey],
            status = row[JobsTable.status],
            createdAt = row[JobsTable.createdAt].toString(),
            totalCostUsd = row[JobsTable.totalCostUsd]?.toPlainString() ?: "-",
            numTurns = row[JobsTable.numTurns],
        )
    }
}

internal fun formatJobRows(rows: List<JobRow>): String {
    if (rows.isEmpty()) return "No runs found."
    return buildString {
        appendLine("%-36s  %-10s  %-12s  %-24s  %-8s  %s".format(
            "job_id", "ticket", "status", "created_at", "cost_usd", "turns",
        ))
        appendLine("-".repeat(110))
        rows.forEach { r ->
            appendLine("%-36s  %-10s  %-12s  %-24s  %-8s  %s".format(
                r.jobId, r.ticketKey, r.status, r.createdAt,
                r.totalCostUsd, r.numTurns?.toString() ?: "-",
            ))
        }
    }
}
