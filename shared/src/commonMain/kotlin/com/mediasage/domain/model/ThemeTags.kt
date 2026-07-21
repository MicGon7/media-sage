package com.mediasage.domain.model

object ThemeTags {
    private const val DELIMITER = ","

    fun fromStorage(stored: String): List<String> = clean(stored.split(DELIMITER))

    fun toStorage(tags: List<String>): String = clean(tags).joinToString(DELIMITER)

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
