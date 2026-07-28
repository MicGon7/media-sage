package com.mediasage.data.repository

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

interface MemorizedQuoteRemoteDataSource {
    suspend fun push(row: MemorizedQuoteRow)
    suspend fun fetch(userId: String): MemorizedQuoteRow?
}

@Serializable
data class MemorizedQuoteRow(
    @SerialName("user_id")
    val userId: String,
    @SerialName("figure_server_id")
    val figureServerId: Long,
    @SerialName("quote_text")
    val quoteText: String,
    val source: String = "",
    val themes: List<String> = emptyList(),
)
