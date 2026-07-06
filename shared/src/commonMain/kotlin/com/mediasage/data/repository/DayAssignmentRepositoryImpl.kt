package com.mediasage.data.repository

import com.mediasage.data.local.dao.DayAssignmentDao
import com.mediasage.data.local.dao.FigureDao
import com.mediasage.data.local.dao.ScheduleOverrideDao
import com.mediasage.data.local.entity.DayAssignmentEntity
import com.mediasage.data.local.entity.ScheduleOverrideEntity
import com.mediasage.data.remote.MediaSageApi
import com.mediasage.domain.model.DayAssignment
import com.mediasage.domain.model.LensFilter
import com.mediasage.domain.repository.DayAssignmentRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withTimeoutOrNull

class DayAssignmentRepositoryImpl(
    private val dao: DayAssignmentDao,
    private val figureDao: FigureDao,
    private val api: MediaSageApi,
    private val overrideDao: ScheduleOverrideDao,
) : DayAssignmentRepository {

    override fun observeAssignments(): Flow<Map<Int, DayAssignment>> =
        dao.observeAll().map { entities ->
            entities.associate { entity ->
                entity.dayOfWeek to DayAssignment(
                    figureId = entity.figureId,
                    lens = entity.lens?.let { name -> LensFilter.entries.firstOrNull { it.name == name } },
                )
            }
        }

    override suspend fun assign(dayOfWeek: Int, figureId: Long, lens: LensFilter?) {
        dao.upsert(DayAssignmentEntity(dayOfWeek = dayOfWeek, figureId = figureId, lens = lens?.name))
    }

    override suspend fun clear(dayOfWeek: Int) {
        dao.delete(dayOfWeek)
    }

    override suspend fun setOverride(epochDay: Long, figureId: Long) {
        overrideDao.upsert(ScheduleOverrideEntity(epochDay = epochDay, figureId = figureId))
    }

    override suspend fun clearOverride(epochDay: Long) {
        overrideDao.delete(epochDay)
    }

    override suspend fun resolveReporter(epochDay: Long, dayOfWeek: Int): Long? {
        overrideDao.getByEpochDay(epochDay)?.let { return it.figureId }
        return dao.getByDayOfWeek(dayOfWeek)?.figureId
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
