package com.mediasage.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.mediasage.data.local.entity.HeadlineEntity
import com.mediasage.data.local.entity.ReadHeadlineEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface HeadlineDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(headline: HeadlineEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(headlines: List<HeadlineEntity>)

    @Query("SELECT * FROM headlines ORDER BY publishedAt DESC")
    fun observeAll(): Flow<List<HeadlineEntity>>

    @Query("SELECT * FROM headlines WHERE id = :id")
    suspend fun getById(id: Long): HeadlineEntity?

    @Query("SELECT id FROM headlines WHERE url = :url LIMIT 1")
    suspend fun getIdByUrl(url: String): Long?

    @Query("SELECT * FROM headlines WHERE url = :url LIMIT 1")
    suspend fun getByUrl(url: String): HeadlineEntity?

    @Query("DELETE FROM headlines WHERE fetchedAt < :olderThan")
    suspend fun deleteOlderThan(olderThan: Long)

    @Query("DELETE FROM headlines")
    suspend fun deleteAll()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun markRead(readHeadline: ReadHeadlineEntity)

    @Query("SELECT url FROM read_headlines WHERE userId = :userId")
    fun observeReadUrls(userId: String): Flow<List<String>>

    @Query("SELECT EXISTS(SELECT 1 FROM read_headlines WHERE userId = :userId AND url = :url)")
    suspend fun isRead(userId: String, url: String): Boolean
}
