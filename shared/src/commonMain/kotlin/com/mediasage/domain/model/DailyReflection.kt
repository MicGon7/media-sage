package com.mediasage.domain.model

data class DailyReflection(
    val scriptureReference: String,
    val scriptureText: String,
    val reflection: String,
    val sources: List<String>,
    val tone: String
)
