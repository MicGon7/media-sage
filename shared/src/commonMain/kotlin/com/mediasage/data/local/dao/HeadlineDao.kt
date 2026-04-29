package com.mediasage.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.mediasage.data.local.entity.HeadlineEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface HeadlineDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(headline: HeadlineEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(headlines: List<HeadlineEntity>)

    @Query("SELECT * FROM headlines ORDER BY publishedAt DESC")
    fun getAll(): Flow<List<HeadlineEntity>>

    @Query("SELECT * FROM headlines WHERE id = :id")
    suspend fun getById(id: Long): HeadlineEntity?

    @Query("SELECT id FROM headlines WHERE url = :url LIMIT 1")
    suspend fun getIdByUrl(url: String): Long?

    @Query("DELETE FROM headlines WHERE fetchedAt < :olderThan")
    suspend fun deleteOlderThan(olderThan: Long)

    @Query("DELETE FROM headlines")
    suspend fun deleteAll()
}
