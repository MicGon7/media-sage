package com.mediasage.data.repository

import com.mediasage.data.local.dao.EncouragementDao
import com.mediasage.data.mapper.toDomain
import com.mediasage.data.mapper.toEntity
import com.mediasage.data.remote.EncourageRequestDto
import com.mediasage.data.remote.MediaSageApi
import com.mediasage.domain.model.Encouragement
import com.mediasage.domain.repository.EncouragementRepository

class EncouragementRepositoryImpl(
    private val api: MediaSageApi,
    private val encouragementDao: EncouragementDao
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

        val recentFigures = encouragementDao.getRecentFigureNames(RECENT_FIGURES_LIMIT)
        val encouragement = api.encourage(
            EncourageRequestDto(
                headlineTitle = headlineTitle,
                articleUrl = articleUrl,
                articleSnippet = articleSnippet,
                recentFigures = recentFigures
            )
        ).toDomain()

        articleUrl?.let {
            encouragementDao.insert(
                encouragement.toEntity(it, headlineTitle, headlineSource, headlineImageUrl, currentTimeMillis())
            )
        }
        return encouragement
    }

    companion object {
        private const val RECENT_FIGURES_LIMIT = 10
    }
}
