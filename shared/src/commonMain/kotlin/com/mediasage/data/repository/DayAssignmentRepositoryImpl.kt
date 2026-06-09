package com.mediasage.data.repository

import com.mediasage.data.local.dao.DayAssignmentDao
import com.mediasage.data.local.dao.FigureDao
import com.mediasage.data.local.entity.DayAssignmentEntity
import com.mediasage.data.remote.MediaSageApi
import com.mediasage.domain.repository.DayAssignmentRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withTimeoutOrNull

class DayAssignmentRepositoryImpl(
    private val dao: DayAssignmentDao,
    private val figureDao: FigureDao,
    private val api: MediaSageApi,
) : DayAssignmentRepository {

    override fun observeAssignments(): Flow<Map<Int, Long>> =
        dao.observeAll().map { entities -> entities.associate { it.dayOfWeek to it.figureId } }

    override suspend fun assign(dayOfWeek: Int, figureId: Long) {
        dao.upsert(DayAssignmentEntity(dayOfWeek = dayOfWeek, figureId = figureId))
    }

    override suspend fun clear(dayOfWeek: Int) {
        dao.delete(dayOfWeek)
    }

    override suspend fun seedDefaultsIfEmpty() {
        if (dao.countAll() > 0) return

        val defaults = withTimeoutOrNull(5_000) {
            try {
                api.getAssignmentDefaults().map { it.dayOrdinal to it.figureName }
            } catch (e: Exception) {
                null
            }
        } ?: FALLBACK_DEFAULTS

        for ((dayOrdinal, figureName) in defaults) {
            val figure = figureDao.getByNameIgnoreCase(figureName) ?: continue
            dao.upsert(DayAssignmentEntity(dayOfWeek = dayOrdinal, figureId = figure.id))
        }
    }

    companion object {
        val FALLBACK_DEFAULTS = listOf(
            0 to "Augustine of Hippo",
            1 to "Julian of Norwich",
            2 to "Martin Luther",
            3 to "Brother Lawrence",
            4 to "Corrie ten Boom",
            5 to "C.S. Lewis",
            6 to "Mother Teresa",
        )
    }
}
