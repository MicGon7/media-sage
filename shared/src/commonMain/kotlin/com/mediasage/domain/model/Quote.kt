package com.mediasage.domain.model

data class Quote(
    val id: Long,
    val figureId: Long,
    val text: String,
    val source: String,
    val themes: List<String>,
    val verified: Boolean = false,
    val memorized: Boolean = false
)
