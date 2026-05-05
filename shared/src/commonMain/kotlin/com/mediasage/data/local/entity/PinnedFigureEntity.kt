package com.mediasage.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "pinned_figure")
data class PinnedFigureEntity(
    @PrimaryKey val id: Int = 1,
    val figureId: Long?
)
