package com.mediasage.domain.repository

import com.mediasage.domain.model.BriefingDay
import com.mediasage.domain.model.DailyReflection
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

interface DailyReflectionRepository {
    suspend fun getOrFetch(
        figureId: Long,
        figureName: String,
        headlines: List<String>,
        tone: String,
        theme: String? = null
    ): DailyReflection

    fun observeByEpochDayRange(startEpochDay: Long, endEpochDay: Long): Flow<List<BriefingDay>>

    suspend fun getForDay(epochDay: Long, tone: String): DailyReflection?

    suspend fun getEarliestBriefingEpochDay(): Long?

    /** The figure id already briefed for [epochDay], if any — that day's reporter is locked. */
    suspend fun getLockedFigureId(epochDay: Long): Long?

    /** True once [resolve] has settled at least once this process — never true before then. */
    val isResolved: StateFlow<Boolean>

    /**
     * Pushes any locally-generated, not-yet-synced reflections up for [userId] (a no-op when
     * signed out), then pulls and unions in any reflections generated on another device — then
     * flips [isResolved] to `true`, regardless of outcome.
     */
    suspend fun resolve(userId: String?)
}
