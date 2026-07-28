package com.mediasage.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "quotes",
    foreignKeys = [
        ForeignKey(
            entity = FigureEntity::class,
            parentColumns = ["id"],
            childColumns = ["figureId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index("figureId"),
        Index(value = ["figureId", "text"], unique = true)
    ]
)
data class QuoteEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val figureId: Long,
    val text: String,
    val source: String,
    val themes: String,
    val verified: Boolean = false,
    val memorized: Boolean = false,
    // Defaults true: most rows are shared quote-catalog content nobody ever memorized, so they
    // must never look like a pending sync push. Only memorizing flips this to false.
    val synced: Boolean = true,
)
