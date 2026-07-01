package com.mediasage.advisor

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.coroutines.delay
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

private const val MAX_ATTEMPTS = 3
private val RETRY_DELAYS_MS = listOf(1_000L, 2_000L)

@Serializable
internal data class ClaudeRequest(
    val model: String,
    @SerialName("max_tokens") val maxTokens: Int,
    val system: String,
    val messages: List<ClaudeMessage>,
    val tools: List<JsonObject>,
    @SerialName("tool_choice") val toolChoice: JsonObject,
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

private suspend fun callClaude(
    client: HttpClient,
    baseUrl: String,
    authToken: String,
    request: ClaudeRequest,
): JsonElement? = runCatching {
    val response: ClaudeResponse = client.post("$baseUrl/v1/messages") {
        contentType(ContentType.Application.Json)
        header("x-api-key", authToken)
        header("anthropic-version", AnthropicApi.VERSION)
        setBody(request)
    }.body()
    response.content.firstOrNull { it.type == "tool_use" }?.input
}.getOrNull()
