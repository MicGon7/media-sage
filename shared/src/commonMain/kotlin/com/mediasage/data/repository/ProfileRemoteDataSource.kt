package com.mediasage.data.repository

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

interface ProfileRemoteDataSource {
    suspend fun push(row: ProfileRow)
}

@Serializable
data class ProfileRow(
    @SerialName("user_id")
    val userId: String,
    @SerialName("display_name")
    val displayName: String,
)
