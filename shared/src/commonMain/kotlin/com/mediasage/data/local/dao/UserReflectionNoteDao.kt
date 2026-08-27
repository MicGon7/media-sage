package com.mediasage.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.mediasage.data.local.entity.UserReflectionNoteEntity

@Dao
interface UserReflectionNoteDao {
    @Query("SELECT * FROM user_reflection_note WHERE userId = :userId AND id = :id")
    suspend fun get(userId: String, id: String): UserReflectionNoteEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: UserReflectionNoteEntity)

    @Query("SELECT * FROM user_reflection_note WHERE userId = :userId AND synced = 0")
    suspend fun getPendingSync(userId: String): List<UserReflectionNoteEntity>

    @Query("UPDATE user_reflection_note SET synced = 1 WHERE userId = :userId AND id = :id")
    suspend fun markSynced(userId: String, id: String)
}
