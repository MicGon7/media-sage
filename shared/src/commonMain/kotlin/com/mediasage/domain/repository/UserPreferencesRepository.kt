package com.mediasage.domain.repository

import com.mediasage.domain.model.LensFilter
import kotlinx.coroutines.flow.Flow

interface UserPreferencesRepository {
    fun observeLens(): Flow<LensFilter>
    suspend fun saveLens(lens: LensFilter)
    suspend fun initializeIfAbsent()
}
