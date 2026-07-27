package com.mediasage.data.repository

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest

private const val TABLE_NAME = "saved_insight"

class PostgrestSavedInsightRemoteDataSource(
    private val client: SupabaseClient
) : SavedInsightRemoteDataSource {

    override suspend fun push(row: SavedInsightRow) {
        client.postgrest.from(TABLE_NAME).upsert(row)
    }

    override suspend fun delete(userId: String, articleUrl: String) {
        client.postgrest.from(TABLE_NAME).delete {
            filter {
                eq("user_id", userId)
                eq("article_url", articleUrl)
            }
        }
    }

    override suspend fun fetchAll(userId: String): List<SavedInsightRow> =
        client.postgrest.from(TABLE_NAME).select {
            filter { eq("user_id", userId) }
        }.decodeList()
}
