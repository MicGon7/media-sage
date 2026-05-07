package com.mediasage.agent.service

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

private val parserJson = Json { ignoreUnknownKeys = true }

internal fun parseStreamJsonMilestone(line: String): String? = try {
    val obj = parserJson.parseToJsonElement(line).jsonObject
    when (obj["type"]?.jsonPrimitive?.content) {
        "system" -> parseSystemEvent(obj)
        "assistant" -> parseAssistantEvent(obj)
        "user" -> parseUserEvent(obj)
        "result" -> parseResultEvent(obj)
        else -> null
    }
} catch (_: Exception) {
    null
}

private fun parseSystemEvent(obj: JsonObject): String? {
    val model = obj["model"]?.jsonPrimitive?.content ?: return null
    return "init: model=$model"
}

private fun parseAssistantEvent(obj: JsonObject): String? {
    val content = obj["message"]?.jsonObject?.get("content")?.jsonArray ?: return null
    return content.mapNotNull { parseContentBlock(it.jsonObject) }
        .joinToString("\n")
        .takeIf { it.isNotEmpty() }
}

private fun parseContentBlock(block: JsonObject): String? = when (block["type"]?.jsonPrimitive?.content) {
    "tool_use" -> parseToolUseBlock(block)
    "text" -> parseTextBlock(block)
    else -> null
}

private fun parseToolUseBlock(block: JsonObject): String {
    val name = block["name"]?.jsonPrimitive?.content ?: "unknown"
    val input = block["input"]?.jsonObject
    val detail = input?.get("command")?.jsonPrimitive?.content
        ?: input?.get("description")?.jsonPrimitive?.content
        ?: input?.toString()?.take(120)
        ?: ""
    return "tool: $name — ${detail.take(120)}"
}

private fun parseTextBlock(block: JsonObject): String? {
    val text = block["text"]?.jsonPrimitive?.content?.trim() ?: ""
    return if (text.isNotBlank()) "thinking: ${text.take(80)}" else null
}

private fun parseUserEvent(obj: JsonObject): String? {
    val content = obj["message"]?.jsonObject?.get("content")?.jsonArray ?: return null
    return content.mapNotNull { block ->
        val b = block.jsonObject
        if (b["type"]?.jsonPrimitive?.content == "tool_result") parseToolResultBlock(b) else null
    }.joinToString("\n").takeIf { it.isNotEmpty() }
}

private fun parseToolResultBlock(block: JsonObject): String? {
    val isError = block["is_error"]?.jsonPrimitive?.booleanOrNull ?: false
    if (!isError) return null
    val detail = block["content"]?.jsonPrimitive?.content
        ?: block["content"]?.toString()
        ?: ""
    return "tool error: ${detail.take(120)}"
}

private fun parseResultEvent(obj: JsonObject): String {
    val subtype = obj["subtype"]?.jsonPrimitive?.content ?: "unknown"
    val durationMs = obj["duration_ms"]?.jsonPrimitive?.longOrNull
    val costUsd = obj["cost_usd"]?.jsonPrimitive?.doubleOrNull
    val durationStr = if (durationMs != null) " ${durationMs}ms" else ""
    val costStr = if (costUsd != null) " \$${"%.4f".format(costUsd)}" else ""
    return "done — $subtype$durationStr$costStr"
}
