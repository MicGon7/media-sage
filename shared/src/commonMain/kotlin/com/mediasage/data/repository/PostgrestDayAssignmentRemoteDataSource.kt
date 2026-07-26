package com.mediasage.data.repository

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest

private const val TABLE_NAME = "day_assignment"

class PostgrestDayAssignmentRemoteDataSource(
    private val client: SupabaseClient
) : DayAssignmentRemoteDataSource {

    override suspend fun push(userId: String, dayOfWeek: Int, figureServerId: Long, lens: String?) {
        client.postgrest.from(TABLE_NAME).upsert(
            DayAssignmentRow(userId = userId, dayOfWeek = dayOfWeek, figureServerId = figureServerId, lens = lens)
        )
    }

    override suspend fun delete(userId: String, dayOfWeek: Int) {
        client.postgrest.from(TABLE_NAME).delete {
            filter {
                eq("user_id", userId)
                eq("day_of_week", dayOfWeek)
            }
        }
    }

    override suspend fun fetchAll(userId: String): List<DayAssignmentRow> =
        client.postgrest.from(TABLE_NAME).select {
            filter { eq("user_id", userId) }
        }.decodeList()
}
