package com.mediasage.domain.repository

import com.mediasage.domain.model.Quote
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

interface QuoteRepository {
    fun observeAllQuotes(): Flow<List<Quote>>
    fun observeQuotesByFigure(figureId: Long): Flow<List<Quote>>
    suspend fun getQuoteById(id: Long): Quote?
    suspend fun getLatestQuoteForFigure(figureId: Long): Quote?
    suspend fun saveQuote(text: String, source: String, themes: List<String>, figureId: Long)

    /** The single quote the user has deliberately chosen to memorize, or null if none yet. */
    fun observeMemorizedQuote(): Flow<Quote?>

    /** Replaces whichever quote was previously memorized — only one is ever memorized at a time. */
    suspend fun memorizeQuote(figureId: Long, text: String)

    /** True once [resolve] has settled at least once this process — never true before then. */
    val isResolved: StateFlow<Boolean>

    /** Resolves the memorized quote for [userId] (or leaves it local-only, when `null`/signed-out). */
    suspend fun resolve(userId: String?)
}
