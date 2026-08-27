package com.mediasage.data.repository

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest

private const val TABLE_NAME = "user_reflection_note"

class PostgrestUserReflectionNoteRemoteDataSource(
    private val client: SupabaseClient
) : UserReflectionNoteRemoteDataSource {

    override suspend fun push(row: UserReflectionNoteRow) {
        client.postgrest.from(TABLE_NAME).upsert(row)
    }

    override suspend fun fetchAll(userId: String): List<UserReflectionNoteRow> =
        client.postgrest.from(TABLE_NAME).select {
            filter { eq("user_id", userId) }
        }.decodeList()
}
