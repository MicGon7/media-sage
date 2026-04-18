package com.mediasage.domain.model

data class Match(
    val id: Long,
    val headlineId: Long,
    val quoteId: Long,
    val explanation: String,
    val confidence: Float,
    val connectionThemes: List<String>,
    val createdAt: Long
)
