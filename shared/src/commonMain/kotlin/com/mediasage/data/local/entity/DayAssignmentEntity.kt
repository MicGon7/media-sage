package com.mediasage.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "day_assignment")
data class DayAssignmentEntity(
    @PrimaryKey val dayOfWeek: Int,
    val figureId: Long,
    val lens: String? = null,
)
