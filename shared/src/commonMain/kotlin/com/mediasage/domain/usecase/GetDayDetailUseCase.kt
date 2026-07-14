package com.mediasage.domain.usecase

import com.mediasage.domain.model.DayDetailData
import com.mediasage.domain.repository.DailyReflectionRepository
import com.mediasage.domain.repository.EncouragementRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map

/**
 * Combines the two repository reads a day-detail view needs — the one-shot daily reflection and the
 * live stream of encouragements saved that day — into a single [DayDetailData] flow.
 *
 * NiA domain-layer pattern: a use case combines reads from more than one repository so the ViewModel
 * receives one stream. The reflection is fetched once, then merged with the encouragement stream so
 * the detail stays live as encouragements are added or bookmarked.
 */
class GetDayDetailUseCase(
    private val reflectionRepository: DailyReflectionRepository,
    private val encouragementRepository: EncouragementRepository,
) {
    operator fun invoke(epochDay: Long): Flow<DayDetailData> = flow {
        val reflection = reflectionRepository.getForDay(epochDay)
        emitAll(
            encouragementRepository.observeByEpochDay(epochDay).map { encouragements ->
                DayDetailData(reflection = reflection, encouragements = encouragements)
            },
        )
    }
}
