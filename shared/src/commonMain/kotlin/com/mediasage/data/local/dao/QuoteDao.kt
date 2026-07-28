package com.mediasage.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.mediasage.data.local.entity.QuoteEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface QuoteDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(quote: QuoteEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(quotes: List<QuoteEntity>)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnore(quote: QuoteEntity): Long

    @Query("SELECT * FROM quotes WHERE figureId = :figureId")
    fun observeByFigure(figureId: Long): Flow<List<QuoteEntity>>

    @Query("SELECT * FROM quotes WHERE figureId = :figureId ORDER BY id DESC LIMIT 1")
    suspend fun getLatestByFigure(figureId: Long): QuoteEntity?

    @Query("SELECT * FROM quotes WHERE id = :id")
    suspend fun getById(id: Long): QuoteEntity?

    @Query("SELECT * FROM quotes WHERE figureId = :figureId AND text = :text LIMIT 1")
    suspend fun getByFigureAndText(figureId: Long, text: String): QuoteEntity?

    @Query("SELECT * FROM quotes")
    fun observeAll(): Flow<List<QuoteEntity>>

    @Query("SELECT * FROM quotes WHERE memorized = 1 LIMIT 1")
    fun observeMemorizedQuote(): Flow<QuoteEntity?>

    @Query("UPDATE quotes SET memorized = 0 WHERE memorized = 1")
    suspend fun clearMemorized()

    @Query("UPDATE quotes SET memorized = 1, synced = :synced WHERE figureId = :figureId AND text = :text")
    suspend fun setMemorized(figureId: Long, text: String, synced: Boolean)

    /** Replaces whichever quote was previously memorized with [figureId]/[text] — only one at a time. */
    @Transaction
    suspend fun memorize(figureId: Long, text: String) {
        clearMemorized()
        setMemorized(figureId, text, synced = false)
    }

    @Query("SELECT * FROM quotes WHERE memorized = 1 AND synced = 0")
    suspend fun getPendingSync(): List<QuoteEntity>

    @Query("UPDATE quotes SET synced = 1 WHERE figureId = :figureId AND text = :text")
    suspend fun markSynced(figureId: Long, text: String)

    @Query("DELETE FROM quotes WHERE id = :id")
    suspend fun deleteById(id: Long)
}
