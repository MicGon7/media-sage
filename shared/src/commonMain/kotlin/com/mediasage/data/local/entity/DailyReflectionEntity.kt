package com.mediasage.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "daily_reflection")
data class DailyReflectionEntity(
    @PrimaryKey val id: String, // "${figureId}_${epochDay}_${tone}"
    val figureId: Long,
    val epochDay: Long,
    val tone: String,
    val scriptureReference: String,
    val scriptureText: String,
    val reflection: String,
    val sources: List<String>
)
