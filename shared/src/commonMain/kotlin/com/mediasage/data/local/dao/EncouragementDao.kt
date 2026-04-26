package com.mediasage.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.mediasage.data.local.entity.EncouragementEntity

@Dao
interface EncouragementDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(encouragement: EncouragementEntity)

    @Query("SELECT * FROM encouragements WHERE articleUrl = :articleUrl")
    suspend fun getByArticleUrl(articleUrl: String): EncouragementEntity?

    @Query("DELETE FROM encouragements")
    suspend fun deleteAll()
}
