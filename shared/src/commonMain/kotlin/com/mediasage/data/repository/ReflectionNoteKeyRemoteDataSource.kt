package com.mediasage.data.repository

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

interface ReflectionNoteKeyRemoteDataSource {
    /** Plain insert, not upsert — a primary-key conflict is how two racing devices detect a loser. */
    suspend fun push(userId: String, keyMaterialBase64: String)
    suspend fun fetch(userId: String): String?
}

@Serializable
data class ReflectionNoteKeyRow(
    @SerialName("user_id")
    val userId: String,
    @SerialName("key_material")
    val keyMaterial: String,
)
