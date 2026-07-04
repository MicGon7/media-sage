package com.mediasage.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.mediasage.data.local.entity.MatchEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MatchDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(match: MatchEntity): Long

    @Query("SELECT * FROM matches WHERE headlineId = :headlineId")
    suspend fun getByHeadline(headlineId: Long): MatchEntity?

    @Query("SELECT * FROM matches ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<MatchEntity>>

    @Query("SELECT * FROM matches WHERE createdAt >= :start AND createdAt <= :end ORDER BY createdAt DESC")
    fun getByCreatedAtRange(start: Long, end: Long): Flow<List<MatchEntity>>

    @Query("DELETE FROM matches WHERE id = :id")
    suspend fun deleteById(id: Long)
}
