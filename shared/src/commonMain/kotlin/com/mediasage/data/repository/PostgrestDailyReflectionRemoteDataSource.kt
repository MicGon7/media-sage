package com.mediasage.data.repository

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest

private const val TABLE_NAME = "daily_reflection"

class PostgrestDailyReflectionRemoteDataSource(
    private val client: SupabaseClient
) : DailyReflectionRemoteDataSource {

    override suspend fun push(row: DailyReflectionRow) {
        client.postgrest.from(TABLE_NAME).upsert(row)
    }

    override suspend fun fetchAll(userId: String): List<DailyReflectionRow> =
        client.postgrest.from(TABLE_NAME).select {
            filter { eq("user_id", userId) }
        }.decodeList()

    override suspend fun fetchOne(userId: String, epochDay: Long, tone: String, theme: String): DailyReflectionRow? =
        client.postgrest.from(TABLE_NAME).select {
            filter {
                eq("user_id", userId)
                eq("epoch_day", epochDay)
                eq("tone", tone)
                eq("theme", theme)
            }
        }.decodeSingleOrNull()
}
