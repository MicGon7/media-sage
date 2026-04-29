package com.mediasage.data.repository

import com.mediasage.data.local.dao.EncouragementDao
import com.mediasage.data.local.entity.EncouragementEntity
import com.mediasage.data.mapper.toDomain
import com.mediasage.data.mapper.toEntity
import com.mediasage.data.remote.EncourageRequestDto
import com.mediasage.data.remote.MediaSageApi
import com.mediasage.domain.model.Encouragement
import com.mediasage.domain.model.StreamEvent
import com.mediasage.domain.model.StreamField
import com.mediasage.domain.repository.EncouragementRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

class EncouragementRepositoryImpl(
    private val api: MediaSageApi,
    private val encouragementDao: EncouragementDao
) : EncouragementRepository {

    override suspend fun getEncouragement(
        headlineTitle: String,
        headlineSource: String,
        headlineImageUrl: String?,
        articleUrl: String?
    ): Encouragement {
        articleUrl?.let { url ->
            encouragementDao.getByArticleUrl(url)?.let { return it.toDomain() }
        }

        val recentFigures = encouragementDao.getRecentFigureNames(RECENT_FIGURES_LIMIT)
        val encouragement = api.encourage(
            EncourageRequestDto(
                headlineTitle = headlineTitle,
                articleUrl = articleUrl,
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

    override fun streamEncouragement(
        headlineTitle: String,
        headlineSource: String,
        headlineImageUrl: String?,
        articleUrl: String?
    ): Flow<StreamEvent> = flow {
        articleUrl?.let { url ->
            encouragementDao.getByArticleUrl(url)?.let { cached ->
                emit(StreamEvent.Cached(cached.toDomain()))
                return@flow
            }
        }

        val recentFigures = encouragementDao.getRecentFigureNames(RECENT_FIGURES_LIMIT)
        val request = EncourageRequestDto(
            headlineTitle = headlineTitle,
            articleUrl = articleUrl,
            recentFigures = recentFigures
        )

        val fieldValues = mutableMapOf<StreamField, StringBuilder>()
        StreamField.entries.forEach { fieldValues[it] = StringBuilder() }
        var figureImageUrl: String? = null

        api.encourageStream(request).collect { event ->
            when (event) {
                is StreamEvent.FieldDelta -> {
                    fieldValues[event.field]?.append(event.text)
                    emit(event)
                }
                is StreamEvent.Portrait -> {
                    figureImageUrl = event.url
                    emit(event)
                }
                is StreamEvent.Done -> {
                    if (articleUrl != null) {
                        val entity = EncouragementEntity(
                            articleUrl = articleUrl,
                            summary = fieldValues[StreamField.SUMMARY]?.toString()?.ifBlank { null },
                            quoteText = fieldValues[StreamField.QUOTE]?.toString().orEmpty(),
                            figureName = fieldValues[StreamField.FIGURE_NAME]?.toString().orEmpty(),
                            figureRole = fieldValues[StreamField.FIGURE_ROLE]?.toString().orEmpty(),
                            scriptureReference = fieldValues[StreamField.SCRIPTURE_REF]?.toString().orEmpty(),
                            scriptureText = fieldValues[StreamField.SCRIPTURE_TEXT]?.toString().orEmpty(),
                            explanation = fieldValues[StreamField.EXPLANATION]?.toString().orEmpty(),
                            connectionThemes = fieldValues[StreamField.CONNECTION_THEMES]?.toString().orEmpty(),
                            matchTheme = fieldValues[StreamField.MATCH_THEME]?.toString().orEmpty(),
                            tone = fieldValues[StreamField.TONE]?.toString().orEmpty(),
                            figureImageUrl = figureImageUrl,
                            headlineTitle = headlineTitle,
                            headlineSource = headlineSource,
                            headlineImageUrl = headlineImageUrl,
                            cachedAt = currentTimeMillis()
                        )
                        encouragementDao.insert(entity)
                    }
                    emit(StreamEvent.Done)
                }
                is StreamEvent.Cached -> emit(event)
            }
        }
    }

    companion object {
        private const val RECENT_FIGURES_LIMIT = 10
    }
}
