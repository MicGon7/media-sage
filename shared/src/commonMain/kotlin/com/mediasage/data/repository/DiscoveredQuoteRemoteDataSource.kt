package com.mediasage.data.repository

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

interface DiscoveredQuoteRemoteDataSource {
    suspend fun push(row: DiscoveredQuoteRow)
    suspend fun fetchAll(userId: String): List<DiscoveredQuoteRow>
}

@Serializable
data class DiscoveredQuoteRow(
    @SerialName("user_id")
    val userId: String,
    @SerialName("figure_server_id")
    val figureServerId: Long,
    @SerialName("quote_text")
    val quoteText: String,
    val source: String = "",
    val themes: List<String> = emptyList(),
)
