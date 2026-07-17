package com.mediasage.advisor

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.coroutines.delay
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

private const val MAX_ATTEMPTS = 3
private val RETRY_DELAYS_MS = listOf(1_000L, 2_000L)

@Serializable
internal data class ToolDefinition(
    val name: String,
    val description: String,
    @SerialName("input_schema")
    val inputSchema: ToolInputSchema,
)

@Serializable
internal data class ToolInputSchema(
    // Anthropic requires input_schema.type; it must be emitted even though it equals the
    // default, which kotlinx would otherwise omit (encodeDefaults is off on the client Json).
    @EncodeDefault(EncodeDefault.Mode.ALWAYS)
    val type: String = "object",
    val properties: Map<String, PropertySchema>,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val required: List<String> = emptyList(),
)

@Serializable
internal data class PropertySchema(
    val type: String,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val description: String? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val items: PropertySchema? = null,
)

@Serializable
internal data class ToolChoice(
    val type: String,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val name: String? = null,
)

@Serializable
internal data class ClaudeRequest(
    val model: String,
    @SerialName("max_tokens")
    val maxTokens: Int,
    val system: String,
    val messages: List<ClaudeMessage>,
    val tools: List<ToolDefinition>,
    @SerialName("tool_choice")
    val toolChoice: ToolChoice,
)

@Serializable
internal data class ClaudeMessage(val role: String, val content: String)

@Serializable
internal data class ClaudeResponse(val content: List<ContentBlock>)

@Serializable
internal data class ContentBlock(val type: String, val input: JsonElement? = null)

internal suspend fun callClaudeWithRetry(
    client: HttpClient,
    baseUrl: String,
    authToken: String,
    request: ClaudeRequest,
): JsonElement? {
    repeat(MAX_ATTEMPTS) { attempt ->
        val result = callClaude(client, baseUrl, authToken, request)
        if (result != null) return result
        if (attempt < RETRY_DELAYS_MS.size) delay(RETRY_DELAYS_MS[attempt])
    }
    return null
}

private val log = org.slf4j.LoggerFactory.getLogger("ClaudeCall")

private suspend fun callClaude(
    client: HttpClient,
    baseUrl: String,
    authToken: String,
    request: ClaudeRequest,
): JsonElement? = runCatching {
    val response: ClaudeResponse = client.post("$baseUrl/v1/messages") {
        contentType(ContentType.Application.Json)
        header("Authorization", "Bearer $authToken")
        header("anthropic-version", AnthropicApi.VERSION)
        setBody(request)
    }.body()
    response.content.firstOrNull { it.type == "tool_use" }?.input
}.getOrElse { e ->
    log.error("Claude API call failed: ${e.message}", e)
null
}
