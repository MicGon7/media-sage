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

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(encouragement: EncouragementEntity)

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
    fun observeDistinctFigures(): Flow<List<VoiceFigureProjection>>

    @Query("SELECT * FROM encouragements WHERE figureName = :figureName")
    fun observeByFigureName(figureName: String): Flow<List<EncouragementEntity>>

    @Query("SELECT * FROM encouragements WHERE figureId = :figureId")
    fun observeByFigureId(figureId: Long): Flow<List<EncouragementEntity>>

    @MapInfo(keyColumn = "figureName", valueColumn = "count")
    @Query("SELECT figureName, COUNT(*) AS count FROM encouragements GROUP BY figureName")
    fun observeCountByFigureName(): Flow<Map<String, Int>>

    @Query(
        """
        SELECT DISTINCT figureName FROM encouragements
        ORDER BY cachedAt DESC
        LIMIT :limit
        """
    )
    suspend fun getRecentFigureNames(limit: Int): List<String>

    @Query("SELECT * FROM encouragements ORDER BY cachedAt DESC")
    fun observeAll(): Flow<List<EncouragementEntity>>

    @Query("SELECT * FROM encouragements WHERE bookmarked = 1 ORDER BY cachedAt DESC")
    fun observeBookmarked(): Flow<List<EncouragementEntity>>

    @Query("SELECT bookmarked FROM encouragements WHERE articleUrl = :articleUrl")
    fun observeBookmarkState(articleUrl: String): Flow<Boolean>

    // bookmarked on the right-hand side refers to the pre-update value (standard SQL UPDATE
    // semantics) — flipping true -> false (unbookmarking) tombstones the row for a remote
    // delete; flipping false -> true (bookmarking) marks it pending a full-content push.
    @Query(
        """
        UPDATE encouragements SET
            bookmarked = NOT bookmarked,
            synced = 0,
            pendingDelete = CASE WHEN bookmarked THEN 1 ELSE 0 END
        WHERE articleUrl = :articleUrl
        """
    )
    suspend fun toggleBookmark(articleUrl: String)

    @Query("SELECT * FROM encouragements WHERE pendingDelete = 1 OR (bookmarked = 1 AND synced = 0)")
    suspend fun getPendingSync(): List<EncouragementEntity>

    @Query("UPDATE encouragements SET synced = 1 WHERE articleUrl = :articleUrl")
    suspend fun markSynced(articleUrl: String)

    // Resets bookmark-sync state without touching the row's cache content — used both when a
    // tombstone-delete push is confirmed and when a pull finds a bookmark removed on another
    // device. Unlike day_assignment/daily_reflection, this table also holds shared cache
    // content that must survive an unbookmark, so there is no row-purge equivalent here.
    @Query("UPDATE encouragements SET bookmarked = 0, synced = 1, pendingDelete = 0 WHERE articleUrl = :articleUrl")
    suspend fun clearBookmarkState(articleUrl: String)

    // Account-switch guard, scoped to bookmark state only — a full clearAll() would wipe
    // shared cache content that has nothing to do with the previous account's saved insights.
    @Query("UPDATE encouragements SET bookmarked = 0, synced = 1, pendingDelete = 0 WHERE bookmarked = 1")
    suspend fun resetBookmarkStateForAccountSwitch()

    @Query("SELECT articleUrl FROM encouragements WHERE bookmarked = 1 AND synced = 1")
    suspend fun getSyncedBookmarkedArticleUrls(): List<String>

    @Query("SELECT * FROM encouragements WHERE cachedAt >= :startMillis AND cachedAt < :endMillis ORDER BY cachedAt DESC")
    fun observeByDateRange(startMillis: Long, endMillis: Long): Flow<List<EncouragementEntity>>

    @Query("SELECT DISTINCT cachedAt / 86400000 FROM encouragements WHERE cachedAt > 0")
    fun observeActiveEpochDays(): Flow<List<Long>>

    @Query("DELETE FROM encouragements")
    suspend fun deleteAll()
}
