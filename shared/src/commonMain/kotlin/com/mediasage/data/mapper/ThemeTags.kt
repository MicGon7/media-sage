package com.mediasage.data.mapper

private const val THEME_DELIMITER = ","

fun String.toThemeList(): List<String> =
    split(THEME_DELIMITER)
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .distinctBy { it.lowercase() }

fun List<String>.toThemeString(): String =
    map { it.trim() }
        .filter { it.isNotEmpty() }
        .distinctBy { it.lowercase() }
        .joinToString(THEME_DELIMITER)
