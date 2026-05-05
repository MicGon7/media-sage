package com.mediasage.domain.repository

import com.mediasage.domain.model.Headline
import kotlinx.coroutines.flow.Flow

interface HeadlineRepository {
    fun observeHeadlines(): Flow<List<Headline>>
    suspend fun getHeadlineById(id: Long): Headline?
    suspend fun getHeadlineByUrl(url: String): Headline?
    suspend fun refreshHeadlines()
    suspend fun clearOldHeadlines(olderThanMillis: Long)
}
