package com.mediasage.data.mapper

/**
 * Canonical list<->string conversion for figure and quote theme tags. Trims whitespace, drops
 * blank entries, and de-duplicates case-insensitively while keeping the first occurrence's
 * casing and the original order.
 */
fun parseThemeTags(value: String): List<String> = cleanThemeTags(value.split(","))

fun formatThemeTags(themes: List<String>): String = cleanThemeTags(themes).joinToString(",")

private fun cleanThemeTags(tags: List<String>): List<String> {
    val seen = LinkedHashSet<String>()
    val cleaned = mutableListOf<String>()
    for (tag in tags) {
        val trimmed = tag.trim()
        if (trimmed.isEmpty()) continue
        if (seen.add(trimmed.lowercase())) cleaned.add(trimmed)
    }
    return cleaned
}
