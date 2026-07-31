package com.mediasage.data.repository

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest

private const val TABLE_NAME = "discovered_quote"

class PostgrestDiscoveredQuoteRemoteDataSource(
    private val client: SupabaseClient
) : DiscoveredQuoteRemoteDataSource {

    override suspend fun push(row: DiscoveredQuoteRow) {
        client.postgrest.from(TABLE_NAME).upsert(row)
    }

    override suspend fun fetchAll(userId: String): List<DiscoveredQuoteRow> =
        client.postgrest.from(TABLE_NAME).select {
            filter { eq("user_id", userId) }
        }.decodeList()
}
