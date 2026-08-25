package com.mediasage.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.mediasage.data.local.entity.UserReflectionNoteEntity

@Dao
interface UserReflectionNoteDao {
    @Query("SELECT * FROM user_reflection_note WHERE id = :id")
    suspend fun get(id: String): UserReflectionNoteEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: UserReflectionNoteEntity)
}
