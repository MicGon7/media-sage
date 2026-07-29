package com.mediasage.data.repository

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest

private const val TABLE_NAME = "profiles"

class PostgrestProfileRemoteDataSource(
    private val client: SupabaseClient
) : ProfileRemoteDataSource {

    override suspend fun push(row: ProfileRow) {
        client.postgrest.from(TABLE_NAME).upsert(row)
    }
}
