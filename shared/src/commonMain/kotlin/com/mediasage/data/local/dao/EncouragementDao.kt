package com.mediasage.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.MapInfo
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

    @Query("SELECT * FROM encouragements WHERE figureId = :figureId")
    fun getByFigureId(figureId: Long): Flow<List<EncouragementEntity>>

    @MapInfo(keyColumn = "figureName", valueColumn = "count")
    @Query("SELECT figureName, COUNT(*) AS count FROM encouragements GROUP BY figureName")
    fun countByFigureName(): Flow<Map<String, Int>>

    @Query(
        """
        SELECT DISTINCT figureName FROM encouragements
        ORDER BY cachedAt DESC
        LIMIT :limit
        """
    )
    suspend fun getRecentFigureNames(limit: Int): List<String>

    @Query("SELECT * FROM encouragements ORDER BY cachedAt DESC")
    fun getAll(): Flow<List<EncouragementEntity>>

    @Query("SELECT * FROM encouragements WHERE bookmarked = 1 ORDER BY cachedAt DESC")
    fun getBookmarked(): Flow<List<EncouragementEntity>>

    @Query("SELECT bookmarked FROM encouragements WHERE articleUrl = :articleUrl")
    fun observeBookmarkState(articleUrl: String): Flow<Boolean>

    @Query("UPDATE encouragements SET bookmarked = NOT bookmarked WHERE articleUrl = :articleUrl")
    suspend fun toggleBookmark(articleUrl: String)

    @Query("DELETE FROM encouragements")
    suspend fun deleteAll()
}
