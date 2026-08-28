package com.mediasage.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.mediasage.data.local.entity.LocalAccountKeyEntity

@Dao
interface LocalAccountKeyDao {
    @Query("SELECT * FROM local_account_key WHERE userId = :userId")
    suspend fun get(userId: String): LocalAccountKeyEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: LocalAccountKeyEntity)
}
