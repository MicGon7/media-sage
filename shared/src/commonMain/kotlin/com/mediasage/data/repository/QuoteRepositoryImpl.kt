package com.mediasage.data.repository

import com.mediasage.data.local.dao.QuoteDao
import com.mediasage.data.local.entity.QuoteEntity
import com.mediasage.data.mapper.toDomain
import com.mediasage.data.mapper.toThemeTagsString
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

    override suspend fun getLatestQuoteForFigure(figureId: Long): Quote? =
        quoteDao.getLatestByFigure(figureId)?.toDomain()

    override suspend fun saveQuote(text: String, source: String, themes: List<String>, figureId: Long) {
        quoteDao.insertIgnore(
            QuoteEntity(
                figureId = figureId,
                text = text,
                source = source,
                themes = themes.toThemeTagsString(),
            )
        )
    }
}
