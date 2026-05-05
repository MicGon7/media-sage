package com.mediasage.data.repository

import com.mediasage.data.local.dao.QuoteDao
import com.mediasage.data.mapper.toDomain
import com.mediasage.domain.model.Quote
import com.mediasage.domain.repository.QuoteRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class QuoteRepositoryImpl(
    private val quoteDao: QuoteDao
) : QuoteRepository {

    override fun observeAllQuotes(): Flow<List<Quote>> =
        quoteDao.observeAll().map { entities -> entities.map { it.toDomain() } }

    override fun observeQuotesByFigure(figureId: Long): Flow<List<Quote>> =
        quoteDao.observeByFigure(figureId).map { entities -> entities.map { it.toDomain() } }

    override suspend fun getQuoteById(id: Long): Quote? =
        quoteDao.getById(id)?.toDomain()
}
