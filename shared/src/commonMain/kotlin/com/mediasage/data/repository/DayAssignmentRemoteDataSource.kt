package com.mediasage.data.repository

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

interface DayAssignmentRemoteDataSource {
    suspend fun push(userId: String, dayOfWeek: Int, figureServerId: Long, lens: String?)
    suspend fun delete(userId: String, dayOfWeek: Int)
    suspend fun fetchAll(userId: String): List<DayAssignmentRow>
}

@Serializable
data class DayAssignmentRow(
    @SerialName("user_id")
    val userId: String,
    @SerialName("day_of_week")
    val dayOfWeek: Int,
    @SerialName("figure_server_id")
    val figureServerId: Long,
    val lens: String? = null,
)
