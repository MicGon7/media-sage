package com.mediasage.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "encouragements")
data class EncouragementEntity(
    @PrimaryKey val articleUrl: String,
    val summary: String?,
    val quoteText: String,
    val figureName: String,
    val figureRole: String,
    val scriptureReference: String,
    val scriptureText: String,
    val explanation: String,
    val connectionThemes: String,
    val matchTheme: String,
    val tone: String,
    val figureImageUrl: String? = null,
    val headlineTitle: String = ""
)
