package com.mediasage.domain.repository

import com.mediasage.domain.model.Encouragement
import kotlinx.coroutines.flow.Flow

interface EncouragementRepository {
    suspend fun getEncouragement(
        headlineTitle: String,
        headlineSource: String = "",
        headlineImageUrl: String? = null,
        articleUrl: String?,
        articleSnippet: String? = null
    ): Encouragement

    fun observeAll(): Flow<List<Encouragement>>

    fun observeBookmarked(): Flow<List<Encouragement>>

    fun observeCountByFigureName(): Flow<Map<String, Int>>

    fun observeByFigureId(figureId: Long): Flow<List<Encouragement>>

    fun observeIsBookmarked(articleUrl: String): Flow<Boolean>

    suspend fun toggleBookmark(articleUrl: String)
}
