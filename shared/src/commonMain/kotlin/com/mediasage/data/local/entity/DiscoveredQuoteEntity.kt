package com.mediasage.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "discovered_quotes",
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
        Index(value = ["figureId", "quoteText"], unique = true)
    ]
)
data class DiscoveredQuoteEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val figureId: Long,
    val quoteText: String,
    val source: String,
    val themes: String,
    // Defaults false: every discovered quote is new content that hasn't reached the backend yet,
    // unlike the shared quote catalog where most rows are never memorized.
    val synced: Boolean = false,
)
