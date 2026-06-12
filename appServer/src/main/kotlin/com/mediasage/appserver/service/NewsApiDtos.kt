package com.mediasage.appserver.service

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// ---- Response DTOs for GNews API ----

@Serializable
data class GNewsResponse(
    @SerialName("totalArticles")
    val totalArticles: Int,
    @SerialName("articles")
    val articles: List<GNewsArticle>
)

@Serializable
data class GNewsArticle(
    val title: String,
    val description: String = "",
    val content: String = "",
    val url: String,
    val image: String? = null,
    @SerialName("publishedAt")
    val publishedAt: String = "",
    val source: GNewsSource
)

@Serializable
data class GNewsSource(
    val name: String,
    val url: String
)

// ---- Shared domain model returned to the client ----

@Serializable
data class NewsArticle(
    val uuid: String,
    val title: String,
    val description: String = "",
    val keywords: String = "",
    val snippet: String = "",
    val url: String,
    @SerialName("image_url")
    val imageUrl: String = "",
    val language: String = "",
    @SerialName("published_at")
    val publishedAt: String = "",
    val source: String = "",
    val categories: List<String> = emptyList(),
    @SerialName("relevance_score")
    val relevanceScore: Float? = null,
    val locale: String = ""
)
