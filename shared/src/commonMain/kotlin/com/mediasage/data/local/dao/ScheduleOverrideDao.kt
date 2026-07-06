package com.mediasage.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.mediasage.data.local.entity.ScheduleOverrideEntity

@Dao
interface ScheduleOverrideDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: ScheduleOverrideEntity)

    @Query("DELETE FROM schedule_override WHERE epochDay = :epochDay")
    suspend fun delete(epochDay: Long)

    @Query("SELECT * FROM schedule_override WHERE epochDay = :epochDay LIMIT 1")
    suspend fun getByEpochDay(epochDay: Long): ScheduleOverrideEntity?
}
