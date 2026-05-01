package com.mediasage.domain.model

data class Figure(
    val id: Long,
    val name: String,
    val category: FigureCategory,
    val century: String,
    val bio: String = "",
    val role: String = "",
    val lifespan: String = "",
    val themes: List<String> = emptyList(),
    val portraitUrl: String? = null
)

enum class FigureCategory(val displayName: String) {
    THEOLOGIAN("Theologians & Reformers"),
    MYSTIC("Mystics & Contemplatives"),
    CHURCH_FATHER("Church Fathers"),
    SOCIAL_JUSTICE("Social Justice & Public Faith"),
    INTELLECTUAL("Scientists & Intellectuals"),
    MISSIONARY("Missionaries & Servants");

    companion object {
        fun fromString(value: String): FigureCategory =
            entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: THEOLOGIAN
    }
}
