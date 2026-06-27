package com.mediasage.advisor

import com.mediasage.advisor.tools.registerAnalyzeRunTool
import com.mediasage.advisor.tools.registerCompareRunsTool
import com.mediasage.advisor.tools.registerExplainFailureTool
import com.mediasage.advisor.tools.registerFetchTranscriptTool
import com.mediasage.advisor.tools.registerQueryRunsTool
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.server.ServerOptions
import io.modelcontextprotocol.kotlin.sdk.server.StdioServerTransport
import io.modelcontextprotocol.kotlin.sdk.types.Implementation
import io.modelcontextprotocol.kotlin.sdk.types.ServerCapabilities
import kotlinx.coroutines.Job
import kotlinx.coroutines.runBlocking
import kotlinx.io.asSink
import kotlinx.io.asSource
import kotlinx.io.buffered
import org.slf4j.LoggerFactory

private val log = LoggerFactory.getLogger("AdvisorServer")

fun main() {
    val dbUrl = requireEnv("SUPABASE_DB_URL")
    val authToken = requireEnv("ANTHROPIC_AUTH_TOKEN")
    val baseUrl = requireEnv("ANTHROPIC_BASE_URL")

    connectDatabase(dbUrl)

    val client = buildHttpClient()
    val server = buildServer()

    server.registerQueryRunsTool()
    server.registerFetchTranscriptTool()
    server.registerAnalyzeRunTool(client, baseUrl, authToken)
    server.registerCompareRunsTool()
    server.registerExplainFailureTool(client, baseUrl, authToken)

    val done = Job()
    server.onClose { done.complete() }

    log.info("Advisor MCP server starting (stdio transport)")

    val transport = StdioServerTransport(
        inputStream = System.`in`.asSource().buffered(),
        outputStream = System.out.asSink().buffered(),
    )

    runBlocking {
        server.createSession(transport)
        done.join()
    }
}

private fun requireEnv(name: String): String =
    System.getenv(name) ?: error("Missing required env var: $name")

private fun buildHttpClient() = HttpClient(OkHttp) {
    install(ContentNegotiation) { json() }
}

private fun buildServer() = Server(
    serverInfo = Implementation(name = "advisor", version = "1.0.0"),
    options = ServerOptions(
        capabilities = ServerCapabilities(tools = ServerCapabilities.Tools(listChanged = true)),
    ),
)
