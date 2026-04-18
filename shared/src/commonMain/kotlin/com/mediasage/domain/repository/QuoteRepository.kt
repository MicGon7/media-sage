package com.mediasage.domain.repository

import com.mediasage.domain.model.Quote
import kotlinx.coroutines.flow.Flow

interface QuoteRepository {
    fun getAllQuotes(): Flow<List<Quote>>
    fun getQuotesByFigure(figureId: Long): Flow<List<Quote>>
    suspend fun getQuoteById(id: Long): Quote?
}
