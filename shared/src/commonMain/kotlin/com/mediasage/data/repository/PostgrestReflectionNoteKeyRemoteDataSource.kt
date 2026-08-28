package com.mediasage.data.repository

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest

private const val TABLE_NAME = "user_reflection_note_key"

class PostgrestReflectionNoteKeyRemoteDataSource(
    private val client: SupabaseClient
) : ReflectionNoteKeyRemoteDataSource {

    override suspend fun push(userId: String, keyMaterialBase64: String) {
        client.postgrest.from(TABLE_NAME).insert(ReflectionNoteKeyRow(userId, keyMaterialBase64))
    }

    override suspend fun fetch(userId: String): String? =
        client.postgrest.from(TABLE_NAME).select {
            filter { eq("user_id", userId) }
        }.decodeSingleOrNull<ReflectionNoteKeyRow>()?.keyMaterial
}
