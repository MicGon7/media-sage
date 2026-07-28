package com.mediasage.data.repository

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest

private const val TABLE_NAME = "memorized_quote"

class PostgrestMemorizedQuoteRemoteDataSource(
    private val client: SupabaseClient
) : MemorizedQuoteRemoteDataSource {

    override suspend fun push(row: MemorizedQuoteRow) {
        client.postgrest.from(TABLE_NAME).upsert(row)
    }

    override suspend fun fetch(userId: String): MemorizedQuoteRow? =
        client.postgrest.from(TABLE_NAME).select {
            filter { eq("user_id", userId) }
        }.decodeSingleOrNull()
}
