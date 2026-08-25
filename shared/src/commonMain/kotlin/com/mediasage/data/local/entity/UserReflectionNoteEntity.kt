package com.mediasage.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * The reader's own reflection note, kept in its own table separate from [DailyReflectionEntity]'s
 * AI-generated content so a future pass can client-side-encrypt just the user's words without
 * touching anything else.
 */
@Entity(tableName = "user_reflection_note")
data class UserReflectionNoteEntity(
    @PrimaryKey val id: String, // "${epochDay}_${tone}_${theme}" — matches daily_reflection's id
    val noteText: String,
    val updatedAtMillis: Long,
)
