package com.mediasage.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.mediasage.data.local.entity.PinnedFigureEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PinnedFigureDao {
    @Query("SELECT * FROM pinned_figure WHERE id = 1")
    suspend fun get(): PinnedFigureEntity?

    @Query("SELECT * FROM pinned_figure WHERE id = 1")
    fun observe(): Flow<PinnedFigureEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: PinnedFigureEntity)
}
