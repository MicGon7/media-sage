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

// ---- Encourage endpoint DTOs ----

@Serializable
data class EncourageRequestDto(
    val headlineTitle: String,
    val locale: String = "en",
    val articleUrl: String? = null,
    val articleSnippet: String? = null
)

@Serializable
data class EncourageResultDto(
    val summary: String? = null,
    val quoteText: String,
    val figureName: String,
    val figureRole: String,
    val scriptureReference: String,
    val scriptureText: String,
    val explanation: String,
    val connectionThemes: List<String>,
    val matchTheme: String,
    val tone: String,
    val figureImageUrl: String? = null
)

// ---- Legacy Match endpoint DTOs (deprecated — TODO MS-46) ----

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

// ---- Figure endpoint DTOs ----

@Serializable
data class FiguresResponse(
    val syncedAt: Long,
    val figures: List<FigureDto>
)

@Serializable
data class FigureDto(
    val id: Long,
    val name: String,
    val category: String,
    val century: String,
    val role: String = "",
    val lifespan: String = "",
    val bio: String = "",
    val themes: String = "",
    @SerialName("portraitUrl")
    val portraitUrl: String? = null,
    val isEnabled: Boolean = true,
    val updatedAt: Long = 0
)

// ---- Daily Reflection endpoint DTOs ----

@Serializable
data class DailyReflectionRequestDto(
    @SerialName("figureId")
    val figureId: Long,
    @SerialName("figureName")
    val figureName: String,
    val headlines: List<String> = emptyList(),
    val tone: String = "morning",
    val dayOfWeek: String = "",
    val previousScriptures: List<String> = emptyList(),
    val previousReflections: List<String> = emptyList(),
    val theme: String? = null
)

@Serializable
data class DailyReflectionResponseDto(
    val scriptureReference: String,
    val scriptureText: String,
    val reflection: String,
    val sources: List<String>,
    val tone: String
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
