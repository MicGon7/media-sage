package com.mediasage.data.repository

import com.mediasage.data.local.dao.HeadlineDao
import com.mediasage.data.mapper.toDomain
import com.mediasage.data.mapper.toEntity
import com.mediasage.data.remote.MediaSageApi
import com.mediasage.domain.model.Headline
import com.mediasage.domain.repository.HeadlineRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class HeadlineRepositoryImpl(
    private val headlineDao: HeadlineDao,
    private val api: MediaSageApi
) : HeadlineRepository {

    override fun observeHeadlines(): Flow<List<Headline>> =
        headlineDao.observeAll().map { entities -> entities.map { it.toDomain() } }

    override suspend fun getHeadlineById(id: Long): Headline? =
        headlineDao.getById(id)?.toDomain()

    override suspend fun getHeadlineByUrl(url: String): Headline? =
        headlineDao.getByUrl(url)?.toDomain()

    override suspend fun refreshHeadlines() {
        // Server tags each headline with one of 7 categories and self-limits via interleaveCategories —
        // a generous client limit just returns everything cached without over-fetching, so the local
        // pool holds enough per-category data for the Headlines screen's category filter to be meaningful.
        val dtos = api.getHeadlines(limit = FETCH_LIMIT)
        val now = currentTimeMillis()
        val readUrls = headlineDao.getReadUrls().toSet()
        val entities = dtos.map { it.toEntity(fetchedAt = now).copy(isRead = it.url in readUrls) }
        headlineDao.deleteAll()
        headlineDao.insertAll(entities)
    }

    override suspend fun clearOldHeadlines(olderThanMillis: Long) {
        headlineDao.deleteOlderThan(olderThanMillis)
    }

    override suspend fun markAsRead(url: String) {
        headlineDao.markRead(url)
    }

    private companion object {
        const val FETCH_LIMIT = 100
    }
}

internal expect fun currentTimeMillis(): Long
