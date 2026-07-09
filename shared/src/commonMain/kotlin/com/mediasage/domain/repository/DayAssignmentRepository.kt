package com.mediasage.domain.repository

import com.mediasage.domain.model.DayAssignment
import com.mediasage.domain.model.LensFilter
import kotlinx.coroutines.flow.Flow

interface DayAssignmentRepository {
    fun observeAssignments(): Flow<Map<Int, DayAssignment>>
    fun observeOverridesByEpochDayRange(start: Long, end: Long): Flow<Map<Long, Long>>
    suspend fun assign(dayOfWeek: Int, figureId: Long, lens: LensFilter? = null)
    suspend fun clear(dayOfWeek: Int)
    suspend fun seedDefaultsIfEmpty()
    suspend fun setOverride(epochDay: Long, figureId: Long)
    suspend fun clearOverride(epochDay: Long)
    suspend fun resolveReporter(epochDay: Long, dayOfWeek: Int): Long?
}
