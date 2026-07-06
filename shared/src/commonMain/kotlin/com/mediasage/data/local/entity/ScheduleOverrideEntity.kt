package com.mediasage.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "schedule_override")
data class ScheduleOverrideEntity(
    @PrimaryKey val epochDay: Long,
    val figureId: Long,
)
