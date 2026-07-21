package com.mediasage.data.mapper

fun parseThemeTags(stored: String): List<String> = cleanThemeTags(stored.split(","))

fun formatThemeTags(tags: List<String>): String = cleanThemeTags(tags).joinToString(",")

private fun cleanThemeTags(tags: List<String>): List<String> {
    val seen = mutableSetOf<String>()
    val result = mutableListOf<String>()
    for (tag in tags) {
        val trimmed = tag.trim()
        if (trimmed.isEmpty()) continue
        if (seen.add(trimmed.lowercase())) result.add(trimmed)
    }
    return result
}
