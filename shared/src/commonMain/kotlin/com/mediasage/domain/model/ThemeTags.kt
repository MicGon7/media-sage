package com.mediasage.domain.model

private fun normalizeThemeTags(rawTags: List<String>): List<String> {
    val seen = LinkedHashMap<String, String>()
    rawTags
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .forEach { tag -> seen.putIfAbsent(tag.lowercase(), tag) }
    return seen.values.toList()
}

fun String.toThemeTagList(): List<String> = normalizeThemeTags(split(","))

fun List<String>.toThemeTagString(): String = normalizeThemeTags(this).joinToString(",")
