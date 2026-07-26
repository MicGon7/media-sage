package com.mediasage.domain.model

data class DailyReflection(
    val scriptureReference: String,
    val scriptureText: String,
    val insight: String,
    val implication: String,
    val inspiration: String,
    val sources: List<String>,
    val tone: String,
    val theme: String? = null
)
