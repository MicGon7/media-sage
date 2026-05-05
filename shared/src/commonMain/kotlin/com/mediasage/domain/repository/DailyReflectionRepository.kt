package com.mediasage.domain.repository

import com.mediasage.domain.model.DailyReflection

interface DailyReflectionRepository {
    suspend fun getOrFetch(
        figureId: Long,
        figureName: String,
        headlines: List<String>,
        tone: String
    ): DailyReflection
}
