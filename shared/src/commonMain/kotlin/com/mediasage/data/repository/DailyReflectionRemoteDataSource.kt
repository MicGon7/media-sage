package com.mediasage.data.repository

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

interface DailyReflectionRemoteDataSource {
    suspend fun push(row: DailyReflectionRow)
    suspend fun fetchAll(userId: String): List<DailyReflectionRow>
    suspend fun fetchOne(userId: String, epochDay: Long, tone: String, theme: String): DailyReflectionRow?
}

@Serializable
data class DailyReflectionRow(
    @SerialName("user_id")
    val userId: String,
    @SerialName("epoch_day")
    val epochDay: Long,
    val tone: String,
    val theme: String,
    @SerialName("figure_server_id")
    val figureServerId: Long,
    @SerialName("scripture_reference")
    val scriptureReference: String,
    @SerialName("scripture_text")
    val scriptureText: String,
    val insight: String,
    val implication: String,
    val inspiration: String,
    val sources: List<String>,
    val challenge: String? = null,
)
