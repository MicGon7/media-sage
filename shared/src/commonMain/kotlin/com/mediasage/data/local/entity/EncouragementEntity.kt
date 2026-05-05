package com.mediasage.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "encouragements",
    indices = [Index(value = ["figureName", "quoteText"], unique = true)]
)
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
    val headlineTitle: String = "",
    val headlineSource: String = "",
    val headlineImageUrl: String? = null,
    val cachedAt: Long = 0L,
    val bookmarked: Boolean = false,
    val figureId: Long? = null
)
