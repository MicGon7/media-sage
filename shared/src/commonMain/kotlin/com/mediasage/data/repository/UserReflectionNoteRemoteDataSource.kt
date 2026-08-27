package com.mediasage.data.repository

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

interface UserReflectionNoteRemoteDataSource {
    suspend fun push(row: UserReflectionNoteRow)
    suspend fun fetchAll(userId: String): List<UserReflectionNoteRow>
}

@Serializable
data class UserReflectionNoteRow(
    @SerialName("user_id")
    val userId: String,
    val id: String,
    @SerialName("note_text")
    val noteText: String,
    @SerialName("updated_at_millis")
    val updatedAtMillis: Long,
)
