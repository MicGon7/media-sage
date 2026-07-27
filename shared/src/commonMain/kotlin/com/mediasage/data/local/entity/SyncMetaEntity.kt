package com.mediasage.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "sync_meta")
data class SyncMetaEntity(
    @PrimaryKey val id: Int = 1,
    val lastFigureSyncAt: Long? = null,
    val lastDayAssignmentSyncUserId: String? = null,
    val lastDailyReflectionSyncUserId: String? = null,
)
