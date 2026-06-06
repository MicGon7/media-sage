package com.mediasage.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.mediasage.data.local.entity.DayAssignmentEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DayAssignmentDao {

    @Query("SELECT * FROM day_assignment")
    fun observeAll(): Flow<List<DayAssignmentEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: DayAssignmentEntity)

    @Query("DELETE FROM day_assignment WHERE dayOfWeek = :dayOfWeek")
    suspend fun delete(dayOfWeek: Int)
}
