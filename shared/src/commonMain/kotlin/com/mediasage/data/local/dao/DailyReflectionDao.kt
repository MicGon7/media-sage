package com.mediasage.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.mediasage.data.local.entity.DailyReflectionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DailyReflectionDao {
    @Query("SELECT * FROM daily_reflection WHERE figureId = :figureId AND epochDay = :epochDay AND tone = :tone AND theme = :theme")
    suspend fun get(figureId: Long, epochDay: Long, tone: String, theme: String): DailyReflectionEntity?

    @Query("SELECT * FROM daily_reflection WHERE figureId = :figureId AND epochDay = :epochDay")
    suspend fun getAllForDay(figureId: Long, epochDay: Long): List<DailyReflectionEntity>

    @Query("SELECT DISTINCT scriptureReference FROM daily_reflection WHERE epochDay = :epochDay")
    suspend fun getAllScripturesForDay(epochDay: Long): List<String>

    @Query("SELECT DISTINCT scriptureReference FROM daily_reflection WHERE figureId = :figureId AND epochDay >= :fromDay AND epochDay < :today")
    suspend fun getRecentScripturesForFigure(figureId: Long, fromDay: Long, today: Long): List<String>

    @Query("SELECT * FROM daily_reflection WHERE epochDay >= :start AND epochDay <= :end ORDER BY epochDay ASC")
    fun getByEpochDayRange(start: Long, end: Long): Flow<List<DailyReflectionEntity>>

    @Query("SELECT * FROM daily_reflection WHERE epochDay = :epochDay AND tone = :tone LIMIT 1")
    suspend fun getForDayAndTone(epochDay: Long, tone: String): DailyReflectionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: DailyReflectionEntity)
}
