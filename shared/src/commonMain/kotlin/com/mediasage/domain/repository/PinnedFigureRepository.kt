package com.mediasage.domain.repository

import kotlinx.coroutines.flow.Flow

interface PinnedFigureRepository {
    fun observePinnedFigureId(): Flow<Long?>
    suspend fun setPinnedFigureId(figureId: Long?)
}
