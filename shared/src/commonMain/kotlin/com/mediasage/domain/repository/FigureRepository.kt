package com.mediasage.domain.repository

import com.mediasage.domain.model.Figure
import com.mediasage.domain.model.FigureCategory
import kotlinx.coroutines.flow.Flow

interface FigureRepository {
    fun observeAllFigures(): Flow<List<Figure>>
    fun observeFiguresByCategory(category: FigureCategory): Flow<List<Figure>>
    suspend fun getFigureById(id: Long): Figure?
    suspend fun getFigureByName(name: String): Figure?
    suspend fun syncFigures()
}
