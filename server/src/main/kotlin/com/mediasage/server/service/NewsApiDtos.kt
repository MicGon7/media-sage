package com.mediasage.server.service

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// ---- Response DTOs for TheNewsAPI /v1/news/all endpoint ----

@Serializable
data class NewsApiResponse(
    val meta: NewsApiMeta,
    val data: List<NewsArticle>
)

@Serializable
data class NewsApiMeta(
    val found: Int,
    val returned: Int,
    val limit: Int,
    val page: Int
)

@Serializable
data class NewsArticle(
    val uuid: String,
    val title: String,
    val description: String = "",
    val keywords: String = "",
    val snippet: String = "",
    val url: String,
    @SerialName("image_url") val imageUrl: String = "",
    val language: String = "",
    @SerialName("published_at") val publishedAt: String = "",
    val source: String = "",
    val categories: List<String> = emptyList(),
    @SerialName("relevance_score") val relevanceScore: Float? = null,
    val locale: String = ""
)
