package com.mediasage.domain.repository

import com.mediasage.domain.model.DayAssignment
import com.mediasage.domain.model.LensFilter
import kotlinx.coroutines.flow.Flow

interface DayAssignmentRepository {
    fun observeAssignments(): Flow<Map<Int, DayAssignment>>
    suspend fun assign(dayOfWeek: Int, figureId: Long, lens: LensFilter? = null)
    suspend fun clear(dayOfWeek: Int)
    suspend fun seedDefaultsIfEmpty()
}
