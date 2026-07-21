package com.mediasage.data.mapper

fun themeTagsFromStored(stored: String): List<String> {
    val seen = HashSet<String>()
    val tags = mutableListOf<String>()
    for (part in stored.split(",")) {
        val trimmed = part.trim()
        if (trimmed.isEmpty()) continue
        if (seen.add(trimmed.lowercase())) {
            tags.add(trimmed)
        }
    }
    return tags
}

fun themeTagsToStored(tags: List<String>): String =
    themeTagsFromStored(tags.joinToString(",")).joinToString(",")
