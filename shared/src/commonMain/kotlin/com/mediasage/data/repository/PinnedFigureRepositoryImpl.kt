package com.mediasage.data.repository

import com.mediasage.data.local.dao.PinnedFigureDao
import com.mediasage.data.local.entity.PinnedFigureEntity
import com.mediasage.domain.repository.PinnedFigureRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class PinnedFigureRepositoryImpl(
    private val dao: PinnedFigureDao
) : PinnedFigureRepository {

    override fun observePinnedFigureId(): Flow<Long?> =
        dao.observe().map { it?.figureId }

    override suspend fun setPinnedFigureId(figureId: Long?) {
        dao.upsert(PinnedFigureEntity(figureId = figureId))
    }
}
