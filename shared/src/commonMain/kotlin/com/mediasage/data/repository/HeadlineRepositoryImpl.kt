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
        val dtos = api.getHeadlines()
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
}

internal expect fun currentTimeMillis(): Long
