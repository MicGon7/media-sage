package com.mediasage.data.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// ---- News endpoint DTOs ----

@Serializable
data class NewsArticleDto(
    val uuid: String,
    val title: String,
    val description: String = "",
    val snippet: String = "",
    val url: String,
    @SerialName("image_url")
    val imageUrl: String = "",
    @SerialName("published_at")
    val publishedAt: String = "",
    val source: String = "",
    val categories: List<String> = emptyList()
)

// ---- Analysis/Match endpoint DTOs ----

@Serializable
data class MatchRequestDto(
    val headlineTitle: String,
    val candidates: List<MatchCandidateDto>
)

@Serializable
data class MatchCandidateDto(
    val id: Long,
    val figureName: String,
    val text: String,
    val source: String,
    val themes: List<String> = emptyList()
)

@Serializable
data class MatchResultDto(
    val selectedQuoteId: Long,
    val confidence: Float,
    val explanation: String,
    val connectionThemes: List<String>
)

// ---- Scripture endpoint DTOs ----

@Serializable
data class ScriptureVerseDto(
    val id: String,
    val bookId: String = "",
    val chapterId: String = "",
    val reference: String = "",
    val text: String = ""
)

@Serializable
data class ScripturePassageDto(
    val id: String,
    val reference: String = "",
    val content: String = ""
)
