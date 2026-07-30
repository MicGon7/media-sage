package com.mediasage.domain.usecase

import com.mediasage.domain.model.HeadlineFeedEntry
import com.mediasage.domain.repository.EncouragementRepository
import com.mediasage.domain.repository.HeadlineRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

/**
 * Combines headlines with their cached match (once a headline has been opened and read) so the
 * Headlines feed can render a read headline in the saved-card style without a second fetch.
 */
class GetHeadlinesFeedUseCase(
    private val headlineRepository: HeadlineRepository,
    private val encouragementRepository: EncouragementRepository,
) {
    operator fun invoke(): Flow<List<HeadlineFeedEntry>> =
        combine(
            headlineRepository.observeHeadlines(),
            encouragementRepository.observeAll(),
        ) { headlines, encouragements ->
            val byUrl = encouragements.associateBy { it.articleUrl }
            headlines.map { headline ->
                val match = byUrl[headline.url]
                HeadlineFeedEntry(
                    headline = headline,
                    figureName = match?.figureName,
                    figureRole = match?.figureRole,
                    figureImageUrl = match?.figureImageUrl,
                    quotePreview = match?.quoteText,
                    isBookmarked = match?.bookmarked ?: false
                )
            }
        }
}
