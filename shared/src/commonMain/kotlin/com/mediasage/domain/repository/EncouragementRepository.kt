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

    fun getByFigureId(figureId: Long): Flow<List<Encouragement>>

    fun observeIsBookmarked(articleUrl: String): Flow<Boolean>

    suspend fun toggleBookmark(articleUrl: String)
}
