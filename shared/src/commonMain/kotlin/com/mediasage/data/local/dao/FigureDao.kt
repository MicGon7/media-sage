package com.mediasage.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.mediasage.data.local.entity.FigureEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface FigureDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(figure: FigureEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(figures: List<FigureEntity>)

    @Query("SELECT * FROM figures ORDER BY name ASC")
    fun observeAll(): Flow<List<FigureEntity>>

    @Query("SELECT * FROM figures WHERE id = :id")
    suspend fun getById(id: Long): FigureEntity?

    @Query("SELECT * FROM figures WHERE category = :category ORDER BY name ASC")
    fun observeByCategory(category: String): Flow<List<FigureEntity>>

    @Query("SELECT * FROM figures WHERE name = :name LIMIT 1")
    suspend fun getByName(name: String): FigureEntity?

    @Query("SELECT * FROM figures WHERE LOWER(name) = LOWER(:name) LIMIT 1")
    suspend fun getByNameIgnoreCase(name: String): FigureEntity?

    @Query("DELETE FROM figures WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM figures")
    suspend fun deleteAll()
}
