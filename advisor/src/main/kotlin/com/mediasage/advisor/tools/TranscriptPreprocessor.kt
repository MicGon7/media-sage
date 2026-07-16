package com.mediasage.advisor.tools

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

private const val SIZE_THRESHOLD = 400 * 1024
private const val HEAD_TAIL_FRACTION = 0.3

// Tool results are condensed, not dropped: below this size a result is kept verbatim;
// above it, only the head, tail, and "signal" lines (errors, test outcomes, exit status) survive.
private const val TOOL_RESULT_KEEP_WHOLE = 2_000
private const val TOOL_RESULT_HEAD_LINES = 12
private const val TOOL_RESULT_TAIL_LINES = 12
private const val TOOL_RESULT_MAX_SIGNAL_LINES = 40

private val KEPT_TOP_TYPES = setOf("system", "result", "assistant", "user")
private val SIGNAL_REGEX = Regex(
    "(?i)(error|exception|fail|pass|assert|traceback|exit (code|status)|\\bbuild\\b|detekt|✗|✓|⨯)",
)

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
        return transformByRole(topType, obj) ?: line
    }
    val role = obj["message"]?.jsonObject?.get("role")?.jsonPrimitive?.content
    return transformByRole(role, obj)
}

private fun transformByRole(role: String?, obj: JsonObject): String? = when (role) {
    "assistant" -> stripThinking(obj)
    "user" -> condenseToolResults(obj)
    else -> null
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
    return withContent(obj, message, stripped)
}

private fun condenseToolResults(obj: JsonObject): String {
    val message = obj["message"]?.jsonObject ?: return obj.toString()
    val content = message["content"]?.jsonArray ?: return obj.toString()
    val condensed = buildJsonArray { content.forEach { add(condenseBlock(it)) } }
    return withContent(obj, message, condensed)
}

private fun condenseBlock(block: JsonElement): JsonElement {
    val blockObj = runCatching { block.jsonObject }.getOrNull() ?: return block
    if (blockObj["type"]?.jsonPrimitive?.content != "tool_result") return block
    val contentEl = blockObj["content"] ?: return block
    return buildJsonObject {
        blockObj.forEach { (k, v) -> if (k != "content") put(k, v) }
        put("content", condenseContent(contentEl))
    }
}

// Tool-result content is either a plain string or an array of text blocks; condense both shapes.
private fun condenseContent(el: JsonElement): JsonElement = when (el) {
    is JsonPrimitive -> if (el.isString) JsonPrimitive(condenseText(el.content)) else el
    is JsonArray -> buildJsonArray { el.forEach { add(condenseTextBlock(it)) } }
    else -> el
}

private fun condenseTextBlock(block: JsonElement): JsonElement {
    val blockObj = runCatching { block.jsonObject }.getOrNull() ?: return block
    if (blockObj["type"]?.jsonPrimitive?.content != "text") return block
    val text = blockObj["text"]?.jsonPrimitive?.content ?: return block
    return buildJsonObject {
        blockObj.forEach { (k, v) -> if (k != "text") put(k, v) }
        put("text", condenseText(text))
    }
}

private fun condenseText(text: String): String {
    if (text.length <= TOOL_RESULT_KEEP_WHOLE) return text
    val lines = text.lines()
    if (lines.size <= TOOL_RESULT_HEAD_LINES + TOOL_RESULT_TAIL_LINES) return truncateChars(text)
    val middle = lines.subList(TOOL_RESULT_HEAD_LINES, lines.size - TOOL_RESULT_TAIL_LINES)
    val signal = middle.filter { SIGNAL_REGEX.containsMatchIn(it) }.take(TOOL_RESULT_MAX_SIGNAL_LINES)
    val marker = "... [${middle.size} middle lines condensed, ${signal.size} signal lines kept] ..."
    return (lines.take(TOOL_RESULT_HEAD_LINES) + marker + signal + lines.takeLast(TOOL_RESULT_TAIL_LINES))
        .joinToString("\n")
}

// A few very long lines can exceed the size limit without exceeding the line count; clip by chars.
private fun truncateChars(text: String): String {
    val half = TOOL_RESULT_KEEP_WHOLE / 2
    val trimmed = text.length - TOOL_RESULT_KEEP_WHOLE
    return text.take(half) + "\n... [$trimmed chars condensed] ...\n" + text.takeLast(half)
}

private fun withContent(obj: JsonObject, message: JsonObject, newContent: JsonArray): String =
    buildJsonObject {
        obj.forEach { (k, v) -> if (k != "message") put(k, v) }
        put("message", buildJsonObject {
            message.forEach { (k, v) -> if (k != "content") put(k, v) }
            put("content", newContent)
        })
    }.toString()

private fun trimHeadTail(text: String): String {
    val lines = text.lines()
    val keep = (lines.size * HEAD_TAIL_FRACTION).toInt().coerceAtLeast(1)
    return (lines.take(keep) + listOf("... [transcript trimmed for size] ...") + lines.takeLast(keep))
        .joinToString("\n")
}
