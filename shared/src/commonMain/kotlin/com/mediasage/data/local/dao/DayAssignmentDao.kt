package com.mediasage.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.mediasage.data.local.entity.DayAssignmentEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DayAssignmentDao {

    @Query("SELECT * FROM day_assignment WHERE pendingDelete = 0")
    fun observeAll(): Flow<List<DayAssignmentEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: DayAssignmentEntity)

    @Query("UPDATE day_assignment SET pendingDelete = 1, synced = 0 WHERE dayOfWeek = :dayOfWeek")
    suspend fun markPendingDelete(dayOfWeek: Int)

    @Query("DELETE FROM day_assignment WHERE dayOfWeek = :dayOfWeek")
    suspend fun purge(dayOfWeek: Int)

    @Query("DELETE FROM day_assignment")
    suspend fun clearAll()

    @Query("SELECT COUNT(*) FROM day_assignment WHERE pendingDelete = 0")
    suspend fun countAll(): Int

    @Query("SELECT * FROM day_assignment WHERE dayOfWeek = :dayOfWeek AND pendingDelete = 0 LIMIT 1")
    suspend fun getByDayOfWeek(dayOfWeek: Int): DayAssignmentEntity?

    @Query("SELECT * FROM day_assignment WHERE dayOfWeek = :dayOfWeek LIMIT 1")
    suspend fun getRawByDayOfWeek(dayOfWeek: Int): DayAssignmentEntity?

    @Query("SELECT * FROM day_assignment WHERE synced = 0")
    suspend fun getPendingSync(): List<DayAssignmentEntity>

    @Query("UPDATE day_assignment SET synced = 1 WHERE dayOfWeek = :dayOfWeek")
    suspend fun markSynced(dayOfWeek: Int)
}
