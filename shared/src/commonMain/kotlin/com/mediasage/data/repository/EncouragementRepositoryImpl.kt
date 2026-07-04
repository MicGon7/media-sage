package com.mediasage.data.repository

import com.mediasage.data.local.dao.EncouragementDao
import com.mediasage.data.local.dao.FigureDao
import com.mediasage.data.mapper.toDomain
import com.mediasage.data.mapper.toEntity
import com.mediasage.data.remote.EncourageRequestDto
import com.mediasage.data.remote.MediaSageApi
import com.mediasage.domain.model.Encouragement
import com.mediasage.domain.repository.EncouragementRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class EncouragementRepositoryImpl(
    private val api: MediaSageApi,
    private val encouragementDao: EncouragementDao,
    private val figureDao: FigureDao
) : EncouragementRepository {

    override suspend fun getEncouragement(
        headlineTitle: String,
        headlineSource: String,
        headlineImageUrl: String?,
        articleUrl: String?,
        articleSnippet: String?
    ): Encouragement {
        articleUrl?.let { url ->
            encouragementDao.getByArticleUrl(url)?.let { return it.toDomain() }
        }

        val encouragement = api.encourage(
            EncourageRequestDto(
                headlineTitle = headlineTitle,
                articleUrl = articleUrl,
                articleSnippet = articleSnippet
            )
        ).toDomain()

        articleUrl?.let {
            val figureId = figureDao.getByName(encouragement.figureName)?.id
            encouragementDao.insert(
                encouragement.toEntity(it, headlineTitle, headlineSource, headlineImageUrl, currentTimeMillis(), figureId)
            )
        }
        return encouragement
    }

    override fun observeAll(): Flow<List<Encouragement>> =
        encouragementDao.observeAll().map { entities -> entities.map { it.toDomain() } }

    override fun observeBookmarked(): Flow<List<Encouragement>> =
        encouragementDao.observeBookmarked().map { entities -> entities.map { it.toDomain() } }

    override fun observeCountByFigureName(): Flow<Map<String, Int>> =
        encouragementDao.observeCountByFigureName()

    override fun observeByFigureId(figureId: Long): Flow<List<Encouragement>> =
        encouragementDao.observeByFigureId(figureId).map { entities -> entities.map { it.toDomain() } }

    override fun observeIsBookmarked(articleUrl: String): Flow<Boolean> =
        encouragementDao.observeBookmarkState(articleUrl)

    override suspend fun toggleBookmark(articleUrl: String) {
        encouragementDao.toggleBookmark(articleUrl)
    }

    override fun observeByEpochDay(epochDay: Long): Flow<List<Encouragement>> {
        val start = epochDay * MS_PER_DAY
        return encouragementDao.observeByDateRange(start, start + MS_PER_DAY)
            .map { entities -> entities.map { it.toDomain() } }
    }

    override fun observeActiveEpochDays(): Flow<Set<Long>> =
        encouragementDao.observeActiveEpochDays().map { it.toSet() }
}

private const val MS_PER_DAY = 86_400_000L
