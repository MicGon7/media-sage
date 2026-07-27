package com.mediasage.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "daily_reflection")
data class DailyReflectionEntity(
    @PrimaryKey val id: String, // "${epochDay}_${tone}_${theme}" — only one figure is ever locked per epochDay
    val figureId: Long,
    val epochDay: Long,
    val tone: String,
    val theme: String = "NEWS",
    val scriptureReference: String,
    val scriptureText: String,
    val insight: String,
    val implication: String,
    val inspiration: String,
    val sources: List<String>,
    val synced: Boolean = false,
)
