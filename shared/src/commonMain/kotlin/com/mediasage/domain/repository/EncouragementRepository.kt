package com.mediasage.domain.repository

import com.mediasage.domain.model.Encouragement
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

interface EncouragementRepository {

    val isResolved: StateFlow<Boolean>

    suspend fun resolve(userId: String?)
    suspend fun getEncouragement(
        headlineTitle: String,
        headlineSource: String = "",
        headlineImageUrl: String? = null,
        articleUrl: String?,
        articleSnippet: String? = null,
        headlineCategory: String = "",
        headlinePublishedAt: Long = 0L
    ): Encouragement

    fun observeAll(): Flow<List<Encouragement>>

    fun observeBookmarked(): Flow<List<Encouragement>>

    fun observeCountByFigureName(): Flow<Map<String, Int>>

    fun observeByFigureId(figureId: Long): Flow<List<Encouragement>>

    fun observeIsBookmarked(articleUrl: String): Flow<Boolean>

    suspend fun toggleBookmark(articleUrl: String)

    fun observeByEpochDay(epochDay: Long): Flow<List<Encouragement>>

    fun observeActiveEpochDays(): Flow<Set<Long>>
}
