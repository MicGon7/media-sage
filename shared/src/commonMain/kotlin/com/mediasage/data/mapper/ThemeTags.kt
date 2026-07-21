package com.mediasage.data.mapper

/**
 * Canonical conversion between the comma-delimited string used to store figure and quote theme
 * tags and the in-memory list. Cleans whitespace, drops blank entries, and de-duplicates tags that
 * differ only in case or surrounding whitespace, keeping the first occurrence's casing and order.
 */
object ThemeTags {

    fun parse(stored: String): List<String> = clean(stored.split(","))

    fun serialize(tags: List<String>): String = clean(tags).joinToString(",")

    private fun clean(tags: List<String>): List<String> {
        val seen = mutableSetOf<String>()
        val cleaned = mutableListOf<String>()
        for (raw in tags) {
            val trimmed = raw.trim()
            if (trimmed.isEmpty()) continue
            if (seen.add(trimmed.lowercase())) {
                cleaned.add(trimmed)
            }
        }
        return cleaned
    }
}
