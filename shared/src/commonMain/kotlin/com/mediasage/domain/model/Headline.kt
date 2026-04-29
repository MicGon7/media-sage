package com.mediasage.domain.model

data class Headline(
    val id: Long,
    val title: String,
    val source: String,
    val url: String,
    val imageUrl: String?,
    val publishedAt: Long,
    val fetchedAt: Long,
    val snippet: String? = null
)
