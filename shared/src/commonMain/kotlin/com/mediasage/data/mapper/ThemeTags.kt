package com.mediasage.data.mapper

private fun List<String>.deduplicateThemeTags(): List<String> {
    val seen = LinkedHashMap<String, String>()
    for (raw in this) {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) continue
        seen.putIfAbsent(trimmed.lowercase(), trimmed)
    }
    return seen.values.toList()
}

fun String.toThemeTags(): List<String> = split(",").deduplicateThemeTags()

fun List<String>.toThemeString(): String = deduplicateThemeTags().joinToString(",")
