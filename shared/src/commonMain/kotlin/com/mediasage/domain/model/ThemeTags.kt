package com.mediasage.domain.model

/**
 * Canonical conversion between a comma-delimited stored theme string and the in-memory tag list.
 * Cleans whitespace and de-duplicates tags that differ only in case or surrounding spaces,
 * keeping the first occurrence's casing and the input order.
 */
object ThemeTags {

    fun parse(stored: String): List<String> = clean(stored.split(","))

    fun format(tags: List<String>): String = clean(tags).joinToString(",")

    private fun clean(rawTags: List<String>): List<String> {
        val seenKeys = mutableSetOf<String>()
        val cleaned = mutableListOf<String>()
        for (rawTag in rawTags) {
            val trimmed = rawTag.trim()
            if (trimmed.isEmpty()) continue
            if (seenKeys.add(trimmed.lowercase())) cleaned.add(trimmed)
        }
        return cleaned
    }
}
