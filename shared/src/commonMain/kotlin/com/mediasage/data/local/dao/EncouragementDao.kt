package com.mediasage.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.mediasage.data.local.entity.EncouragementEntity
import com.mediasage.data.local.entity.VoiceFigureProjection
import kotlinx.coroutines.flow.Flow

@Dao
interface EncouragementDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(encouragement: EncouragementEntity)

    @Query("SELECT * FROM encouragements WHERE articleUrl = :articleUrl")
    suspend fun getByArticleUrl(articleUrl: String): EncouragementEntity?

    @Query(
        """
        SELECT figureName, MAX(figureRole) AS figureRole, MAX(figureImageUrl) AS figureImageUrl
        FROM encouragements
        GROUP BY figureName
        ORDER BY figureName ASC
        """
    )
    fun getDistinctFigures(): Flow<List<VoiceFigureProjection>>

    @Query("SELECT * FROM encouragements WHERE figureName = :figureName")
    fun getByFigureName(figureName: String): Flow<List<EncouragementEntity>>

    @Query(
        """
        SELECT DISTINCT figureName FROM encouragements
        ORDER BY cachedAt DESC
        LIMIT :limit
        """
    )
    suspend fun getRecentFigureNames(limit: Int): List<String>

    @Query("DELETE FROM encouragements")
    suspend fun deleteAll()
}
