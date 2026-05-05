package com.mediasage.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.mediasage.data.local.entity.DailyReflectionEntity

@Dao
interface DailyReflectionDao {
    @Query("SELECT * FROM daily_reflection WHERE figureId = :figureId AND epochDay = :epochDay AND tone = :tone")
    suspend fun get(figureId: Long, epochDay: Long, tone: String): DailyReflectionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: DailyReflectionEntity)
}
