package com.mediasage.domain.repository

import com.mediasage.domain.model.Quote
import kotlinx.coroutines.flow.Flow

interface QuoteRepository {
    fun observeAllQuotes(): Flow<List<Quote>>
    fun observeQuotesByFigure(figureId: Long): Flow<List<Quote>>
    suspend fun getQuoteById(id: Long): Quote?
    suspend fun getLatestQuoteForFigure(figureId: Long): Quote?
    suspend fun saveQuote(text: String, source: String, themes: List<String>, figureId: Long)
}
