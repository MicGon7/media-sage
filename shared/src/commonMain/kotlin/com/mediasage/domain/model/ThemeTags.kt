package com.mediasage.domain.model

/**
 * Canonical conversion between a comma-delimited stored theme string and the in-memory tag list.
 * Tags are trimmed, blank entries dropped, and duplicates (case- and whitespace-insensitive)
 * collapsed to their first occurrence.
 */
object ThemeTags {
    private const val DELIMITER = ","

    fun toList(stored: String): List<String> = clean(stored.split(DELIMITER))

    fun toStored(tags: List<String>): String = clean(tags).joinToString(DELIMITER)

    private fun clean(tags: List<String>): List<String> {
        val seen = mutableSetOf<String>()
        val result = mutableListOf<String>()
        for (tag in tags) {
            val trimmed = tag.trim()
            if (trimmed.isEmpty()) continue
            if (seen.add(trimmed.lowercase())) {
                result.add(trimmed)
            }
        }
        return result
    }
}
