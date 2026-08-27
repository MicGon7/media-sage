package com.mediasage.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * The reader's own reflection note, kept in its own table separate from [DailyReflectionEntity]'s
 * AI-generated content so a future pass can client-side-encrypt just the user's words without
 * touching anything else.
 *
 * Keyed by (userId, id) rather than id alone — like read_headlines (MS-734) — so two accounts
 * signed into the same device can never read or overwrite each other's note under the same
 * reflection id.
 */
@Entity(tableName = "user_reflection_note", primaryKeys = ["userId", "id"])
data class UserReflectionNoteEntity(
    val userId: String,
    val id: String, // "${epochDay}_${tone}_${theme}" — matches daily_reflection's id
    val noteText: String,
    val updatedAtMillis: Long,
    val synced: Boolean = false,
)
