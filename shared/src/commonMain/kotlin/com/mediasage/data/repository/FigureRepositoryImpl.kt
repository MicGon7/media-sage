package com.mediasage.data.repository

import com.mediasage.data.local.dao.FigureDao
import com.mediasage.data.local.dao.SyncMetaDao
import com.mediasage.data.local.entity.SyncMetaEntity
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
    private val syncMetaDao: SyncMetaDao,
    private val api: MediaSageApi
) : FigureRepository {

    override fun observeAllFigures(): Flow<List<Figure>> =
        figureDao.observeAll().map { entities -> entities.map { it.toDomain() } }

    override fun observeFiguresByCategory(category: FigureCategory): Flow<List<Figure>> =
        figureDao.observeByCategory(category.name.lowercase()).map { entities ->
            entities.map { it.toDomain() }
        }

    override suspend fun getFigureById(id: Long): Figure? =
        figureDao.getById(id)?.toDomain()

    override suspend fun getFigureByName(name: String): Figure? =
        figureDao.getByName(name)?.toDomain()

    override suspend fun syncFigures() {
        val lastSyncAt = syncMetaDao.get()?.lastFigureSyncAt
        val syncStartedAt = currentTimeMillis()
        val isFullSync = lastSyncAt == null || syncStartedAt - lastSyncAt > FULL_SYNC_INTERVAL_MS
        val figures = api.getFigures(since = if (isFullSync) null else lastSyncAt)
        if (isFullSync) {
            figureDao.deleteAll()
            figureDao.insertAll(figures.map { it.toEntity() })
        } else if (figures.isNotEmpty()) {
            figureDao.insertAll(figures.map { it.toEntity() })
        }
        syncMetaDao.upsert(SyncMetaEntity(lastFigureSyncAt = syncStartedAt))
    }

    companion object {
        private const val FULL_SYNC_INTERVAL_MS = 7 * 24 * 60 * 60 * 1000L
    }
}
