package com.mediasage.advisor.tools

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

private const val SIZE_THRESHOLD = 400 * 1024
private const val HEAD_TAIL_FRACTION = 0.3
private val KEPT_TOP_TYPES = setOf("system", "result", "assistant")

internal fun preprocessTranscript(transcript: String): String {
    val filtered = filterAndProcess(transcript)
    return if (filtered.length <= SIZE_THRESHOLD) filtered else trimHeadTail(filtered)
}

private fun filterAndProcess(transcript: String): String =
    transcript.lines()
        .filter { it.isNotBlank() }
        .mapNotNull { processLine(it) }
        .joinToString("\n")

private fun processLine(line: String): String? {
    val obj = runCatching { Json.parseToJsonElement(line).jsonObject }.getOrNull() ?: return null
    val topType = obj["type"]?.jsonPrimitive?.content
    if (topType != null) {
        if (topType !in KEPT_TOP_TYPES) return null
        return if (topType == "assistant") stripThinking(obj) else line
    }
    val role = obj["message"]?.jsonObject?.get("role")?.jsonPrimitive?.content
    return if (role == "assistant") stripThinking(obj) else null
}

private fun stripThinking(obj: JsonObject): String {
    val message = obj["message"]?.jsonObject ?: return obj.toString()
    val content = message["content"]?.jsonArray ?: return obj.toString()
    val stripped = buildJsonArray {
        content.forEach { block ->
            val type = runCatching { block.jsonObject["type"]?.jsonPrimitive?.content }.getOrNull()
            if (type != "thinking") add(block)
        }
    }
    return buildJsonObject {
        obj.forEach { (k, v) -> if (k != "message") put(k, v) }
        put("message", buildJsonObject {
            message.forEach { (k, v) -> if (k != "content") put(k, v) }
            put("content", stripped)
        })
    }.toString()
}

private fun trimHeadTail(text: String): String {
    val lines = text.lines()
    val keep = (lines.size * HEAD_TAIL_FRACTION).toInt().coerceAtLeast(1)
    return (lines.take(keep) + listOf("... [transcript trimmed for size] ...") + lines.takeLast(keep))
        .joinToString("\n")
}
