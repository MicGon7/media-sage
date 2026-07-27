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

    @Query("SELECT * FROM daily_reflection WHERE id = :id")
    suspend fun getRawById(id: String): DailyReflectionEntity?

    @Query("SELECT * FROM daily_reflection WHERE figureId = :figureId AND epochDay = :epochDay")
    suspend fun getAllForDay(figureId: Long, epochDay: Long): List<DailyReflectionEntity>

    @Query("SELECT DISTINCT scriptureReference FROM daily_reflection WHERE epochDay = :epochDay")
    suspend fun getAllScripturesForDay(epochDay: Long): List<String>

    @Query("SELECT DISTINCT scriptureReference FROM daily_reflection WHERE figureId = :figureId AND epochDay >= :fromDay AND epochDay < :today")
    suspend fun getRecentScripturesForFigure(figureId: Long, fromDay: Long, today: Long): List<String>

    @Query("SELECT * FROM daily_reflection WHERE epochDay >= :start AND epochDay <= :end ORDER BY epochDay ASC")
    fun getByEpochDayRange(start: Long, end: Long): Flow<List<DailyReflectionEntity>>

    @Query("SELECT MIN(epochDay) FROM daily_reflection")
    suspend fun getEarliestEpochDay(): Long?

    @Query("SELECT * FROM daily_reflection WHERE epochDay = :epochDay AND tone = :tone LIMIT 1")
    suspend fun getForDayAndTone(epochDay: Long, tone: String): DailyReflectionEntity?

    @Query("SELECT figureId FROM daily_reflection WHERE epochDay = :epochDay LIMIT 1")
    suspend fun getFigureIdForDay(epochDay: Long): Long?

    @Query("SELECT * FROM daily_reflection WHERE synced = 0")
    suspend fun getPendingSync(): List<DailyReflectionEntity>

    @Query("UPDATE daily_reflection SET synced = 1 WHERE id = :id")
    suspend fun markSynced(id: String)

    @Query("DELETE FROM daily_reflection")
    suspend fun clearAll()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: DailyReflectionEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIfAbsent(entity: DailyReflectionEntity)
}
