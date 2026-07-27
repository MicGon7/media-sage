package com.mediasage.domain.repository

import com.mediasage.domain.model.DayAssignment
import com.mediasage.domain.model.LensFilter
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

interface DayAssignmentRepository {
    fun observeAssignments(): Flow<Map<Int, DayAssignment>>
    suspend fun assign(dayOfWeek: Int, figureId: Long, lens: LensFilter? = null)
    suspend fun clear(dayOfWeek: Int)
    suspend fun resolveReporter(epochDay: Long, dayOfWeek: Int): Long?

    /** True once [resolve] has settled at least once this process — never true before then. */
    val isResolved: StateFlow<Boolean>

    /**
     * Resolves day-assignment state for [userId] (or defaults, when `null`/signed-out) as a
     * single explicit step. Applies an existing remote schedule when signed in, or falls back to
     * defaults — then flips [isResolved] to `true`, regardless of outcome.
     */
    suspend fun resolve(userId: String?)
}
