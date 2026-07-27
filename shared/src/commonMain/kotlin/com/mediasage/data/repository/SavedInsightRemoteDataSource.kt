package com.mediasage.data.repository

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

interface SavedInsightRemoteDataSource {
    suspend fun push(row: SavedInsightRow)
    suspend fun delete(userId: String, articleUrl: String)
    suspend fun fetchAll(userId: String): List<SavedInsightRow>
}

@Serializable
data class SavedInsightRow(
    @SerialName("user_id")
    val userId: String,
    @SerialName("article_url")
    val articleUrl: String,
    @SerialName("figure_server_id")
    val figureServerId: Long,
    val summary: String? = null,
    @SerialName("quote_text")
    val quoteText: String,
    @SerialName("figure_name")
    val figureName: String,
    @SerialName("figure_role")
    val figureRole: String,
    @SerialName("scripture_reference")
    val scriptureReference: String,
    @SerialName("scripture_text")
    val scriptureText: String,
    val explanation: String,
    @SerialName("connection_themes")
    val connectionThemes: List<String>,
    @SerialName("match_theme")
    val matchTheme: String,
    val tone: String,
    @SerialName("figure_image_url")
    val figureImageUrl: String? = null,
    @SerialName("headline_title")
    val headlineTitle: String = "",
    @SerialName("headline_source")
    val headlineSource: String = "",
    @SerialName("headline_image_url")
    val headlineImageUrl: String? = null,
)
