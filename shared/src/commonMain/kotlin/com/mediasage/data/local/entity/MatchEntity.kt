package com.mediasage.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "matches",
    foreignKeys = [
        ForeignKey(
            entity = HeadlineEntity::class,
            parentColumns = ["id"],
            childColumns = ["headlineId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = QuoteEntity::class,
            parentColumns = ["id"],
            childColumns = ["quoteId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("headlineId"), Index("quoteId")]
)
data class MatchEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val headlineId: Long,
    val quoteId: Long,
    val explanation: String,     // Claude's explanation of why this quote matches
    val confidence: Float,       // 0.0 to 1.0 confidence score from Claude
    val connectionThemes: String, // Comma-separated themes connecting headline to quote
    val createdAt: Long          // Epoch millis
)
