package com.mediasage.data.repository

import com.mediasage.data.local.dao.HeadlineDao
import com.mediasage.data.mapper.toDomain
import com.mediasage.domain.model.Headline
import com.mediasage.domain.repository.HeadlineRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class HeadlineRepositoryImpl(
    private val headlineDao: HeadlineDao
    // TODO: Add remote news API service when MS-10 is complete
) : HeadlineRepository {

    override fun getHeadlines(): Flow<List<Headline>> =
        headlineDao.getAll().map { entities -> entities.map { it.toDomain() } }

    override suspend fun getHeadlineById(id: Long): Headline? =
        headlineDao.getById(id)?.toDomain()

    override suspend fun refreshHeadlines() {
        // TODO: Fetch from remote API, save to Room (MS-10)
        // Cache-first strategy: UI reads from Room via getHeadlines() Flow,
        // this method fetches fresh data from network and updates Room
    }

    override suspend fun clearOldHeadlines(olderThanMillis: Long) {
        headlineDao.deleteOlderThan(olderThanMillis)
    }
}
