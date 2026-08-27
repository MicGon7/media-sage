package com.mediasage.data.repository

import com.mediasage.data.local.dao.HeadlineDao
import com.mediasage.data.local.entity.ReadHeadlineEntity
import com.mediasage.data.mapper.toDomain
import com.mediasage.data.mapper.toEntity
import com.mediasage.data.remote.MediaSageApi
import com.mediasage.domain.model.Headline
import com.mediasage.domain.repository.AuthRepository
import com.mediasage.domain.repository.HeadlineRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map

class HeadlineRepositoryImpl(
    private val headlineDao: HeadlineDao,
    private val api: MediaSageApi,
    private val authRepository: AuthRepository
) : HeadlineRepository {

    override fun observeHeadlines(): Flow<List<Headline>> =
        observeUserId().flatMapLatest { userId ->
            combine(headlineDao.observeAll(), headlineDao.observeReadUrls(userId)) { entities, readUrls ->
                val readSet = readUrls.toSet()
                entities.map { it.toDomain(isRead = it.url in readSet) }
            }
        }

    override suspend fun getHeadlineById(id: Long): Headline? {
        val entity = headlineDao.getById(id) ?: return null
        return entity.toDomain(isRead = headlineDao.isRead(currentUserId(), entity.url))
    }

    override suspend fun getHeadlineByUrl(url: String): Headline? {
        val entity = headlineDao.getByUrl(url) ?: return null
        return entity.toDomain(isRead = headlineDao.isRead(currentUserId(), url))
    }

    override suspend fun refreshHeadlines() {
        // Server tags each headline with one of 7 categories and self-limits via interleaveCategories —
        // a generous client limit just returns everything cached without over-fetching, so the local
        // pool holds enough per-category data for the Headlines screen's category filter to be meaningful.
        // Read state now lives in the separate read_headlines table (MS-734), so it no longer needs to
        // be preserved by hand across this delete-and-reinsert cache refresh.
        val dtos = api.getHeadlines(limit = FETCH_LIMIT)
        val now = currentTimeMillis()
        val entities = dtos.map { it.toEntity(fetchedAt = now) }
        headlineDao.deleteAll()
        headlineDao.insertAll(entities)
    }

    override suspend fun clearOldHeadlines(olderThanMillis: Long) {
        headlineDao.deleteOlderThan(olderThanMillis)
    }

    override suspend fun markAsRead(url: String) {
        headlineDao.markRead(ReadHeadlineEntity(userId = currentUserId(), url = url))
    }

    private fun observeUserId(): Flow<String> =
        authRepository.observeAuthState()
            .map { it?.userId?.takeIf { id -> id.isNotBlank() } ?: ANONYMOUS_USER_ID }
            .distinctUntilChanged()

    private fun currentUserId(): String =
        authRepository.currentSession()?.userId?.takeIf { it.isNotBlank() } ?: ANONYMOUS_USER_ID

    private companion object {
        const val FETCH_LIMIT = 100
        const val ANONYMOUS_USER_ID = ""
    }
}

internal expect fun currentTimeMillis(): Long
