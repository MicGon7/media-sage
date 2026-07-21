package com.mediasage.data.mapper

private const val THEME_DELIMITER = ","

fun String.toThemeTags(): List<String> =
    split(THEME_DELIMITER)
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .distinctBy { it.lowercase() }

fun List<String>.toThemeTagsString(): String =
    map { it.trim() }
        .filter { it.isNotEmpty() }
        .distinctBy { it.lowercase() }
        .joinToString(THEME_DELIMITER)
