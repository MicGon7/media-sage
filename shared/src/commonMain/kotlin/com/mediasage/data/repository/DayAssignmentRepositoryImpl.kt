package com.mediasage.data.repository

import com.mediasage.data.local.dao.DayAssignmentDao
import com.mediasage.data.local.entity.DayAssignmentEntity
import com.mediasage.domain.repository.DayAssignmentRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class DayAssignmentRepositoryImpl(
    private val dao: DayAssignmentDao,
) : DayAssignmentRepository {

    override fun observeAssignments(): Flow<Map<Int, Long>> =
        dao.observeAll().map { entities -> entities.associate { it.dayOfWeek to it.figureId } }

    override suspend fun assign(dayOfWeek: Int, figureId: Long) {
        dao.upsert(DayAssignmentEntity(dayOfWeek = dayOfWeek, figureId = figureId))
    }

    override suspend fun clear(dayOfWeek: Int) {
        dao.delete(dayOfWeek)
    }
}
