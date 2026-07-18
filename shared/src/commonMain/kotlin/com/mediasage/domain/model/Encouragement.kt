package com.mediasage.domain.model

data class Encouragement(
    val summary: String?,
    val quoteText: String,
    val figureName: String,
    val figureRole: String,
    val scriptureReference: String,
    val scriptureText: String,
    val explanation: String,
    val connectionThemes: List<String>,
    val matchTheme: String,
    val tone: String,
    val figureImageUrl: String? = null,
    val headlineTitle: String = "",
    val headlineSource: String = "",
    val headlineImageUrl: String? = null,
    val articleUrl: String? = null,
    val bookmarked: Boolean = false,
    val figureId: Long? = null
)
