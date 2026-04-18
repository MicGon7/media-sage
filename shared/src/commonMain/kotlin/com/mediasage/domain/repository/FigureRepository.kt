package com.mediasage.domain.repository

import com.mediasage.domain.model.Figure
import com.mediasage.domain.model.FigureCategory
import kotlinx.coroutines.flow.Flow

interface FigureRepository {
    fun getAllFigures(): Flow<List<Figure>>
    fun getFiguresByCategory(category: FigureCategory): Flow<List<Figure>>
    suspend fun getFigureById(id: Long): Figure?
}
