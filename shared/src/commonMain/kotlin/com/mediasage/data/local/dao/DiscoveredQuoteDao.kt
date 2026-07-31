package com.mediasage.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.mediasage.data.local.entity.DiscoveredQuoteEntity

@Dao
interface DiscoveredQuoteDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnore(quote: DiscoveredQuoteEntity): Long

    @Query("SELECT * FROM discovered_quotes WHERE figureId = :figureId AND quoteText = :quoteText LIMIT 1")
    suspend fun getByFigureAndText(figureId: Long, quoteText: String): DiscoveredQuoteEntity?

    @Query("SELECT * FROM discovered_quotes WHERE synced = 0")
    suspend fun getPendingSync(): List<DiscoveredQuoteEntity>

    @Query("UPDATE discovered_quotes SET synced = 1 WHERE figureId = :figureId AND quoteText = :quoteText")
    suspend fun markSynced(figureId: Long, quoteText: String)

    // Account-switch guard — marks every local row synced so a different account's sign-in never
    // pushes this device's previous-account discovery history into the new account's table.
    @Query("UPDATE discovered_quotes SET synced = 1")
    suspend fun markAllSyncedForAccountSwitch()
}
