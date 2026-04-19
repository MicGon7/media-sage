package com.mediasage.server.service

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// ---- Request DTOs ----

@Serializable
data class ClaudeRequest(
    val model: String,
    @SerialName("max_tokens")
    val maxTokens: Int,
    val system: String,
    val messages: List<ClaudeMessage>
)

@Serializable
data class ClaudeMessage(
    val role: String,    // "user" or "assistant"
    val content: String
)

// ---- Response DTOs ----

@Serializable
data class ClaudeResponse(
    val id: String,
    val type: String,
    val role: String,
    val content: List<ContentBlock>,
    val model: String,
    @SerialName("stop_reason")
    val stopReason: String? = null,
    val usage: Usage
)

@Serializable
data class ContentBlock(
    val type: String,     // "text"
    val text: String
)

@Serializable
data class Usage(
    @SerialName("input_tokens")
    val inputTokens: Int,
    @SerialName("output_tokens")
    val outputTokens: Int
)

// ---- Error Response ----

@Serializable
data class ClaudeErrorResponse(
    val type: String,
    val error: ClaudeError
)

@Serializable
data class ClaudeError(
    val type: String,     // "authentication_error", "rate_limit_error", etc.
    val message: String
)
