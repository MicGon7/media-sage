package com.mediasage.domain.repository

import com.mediasage.domain.model.BriefingDay
import com.mediasage.domain.model.DailyReflection
import kotlinx.coroutines.flow.Flow

interface DailyReflectionRepository {
    suspend fun getOrFetch(
        figureId: Long,
        figureName: String,
        headlines: List<String>,
        tone: String,
        theme: String? = null
    ): DailyReflection

    fun observeByEpochDayRange(startEpochDay: Long, endEpochDay: Long): Flow<List<BriefingDay>>

    suspend fun getForDay(epochDay: Long): DailyReflection?
}
