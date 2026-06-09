package com.mediasage.domain.repository

import kotlinx.coroutines.flow.Flow

interface DayAssignmentRepository {
    fun observeAssignments(): Flow<Map<Int, Long>>
    suspend fun assign(dayOfWeek: Int, figureId: Long)
    suspend fun clear(dayOfWeek: Int)
    suspend fun seedDefaultsIfEmpty()
}
