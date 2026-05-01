package com.mediasage.data.repository

import com.mediasage.data.local.dao.FigureDao
import com.mediasage.data.mapper.toDomain
import com.mediasage.data.mapper.toEntity
import com.mediasage.data.remote.MediaSageApi
import com.mediasage.domain.model.Figure
import com.mediasage.domain.model.FigureCategory
import com.mediasage.domain.repository.FigureRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class FigureRepositoryImpl(
    private val figureDao: FigureDao,
    private val api: MediaSageApi
) : FigureRepository {

    override fun getAllFigures(): Flow<List<Figure>> =
        figureDao.getAll().map { entities -> entities.map { it.toDomain() } }

    override fun getFiguresByCategory(category: FigureCategory): Flow<List<Figure>> =
        figureDao.getByCategory(category.name.lowercase()).map { entities ->
            entities.map { it.toDomain() }
        }

    override suspend fun getFigureById(id: Long): Figure? =
        figureDao.getById(id)?.toDomain()

    override suspend fun getFigureByName(name: String): Figure? =
        figureDao.getByName(name)?.toDomain()

    override suspend fun syncFigures() {
        val figures = api.getFigures()
        figureDao.deleteAll()
        figureDao.insertAll(figures.map { it.toEntity() })
    }
}
