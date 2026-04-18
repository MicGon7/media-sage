package com.mediasage.domain.model

data class Figure(
    val id: Long,
    val name: String,
    val category: FigureCategory,
    val century: String,
    val description: String
)

enum class FigureCategory {
    THEOLOGIAN,
    MYSTIC,
    MODERN,
    BIBLICAL;

    companion object {
        fun fromString(value: String): FigureCategory =
            entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: THEOLOGIAN
    }
}
