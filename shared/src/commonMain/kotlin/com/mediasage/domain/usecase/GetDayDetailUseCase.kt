package com.mediasage.domain.usecase

import com.mediasage.domain.model.DayDetailData
import com.mediasage.domain.repository.DailyReflectionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Combines the one-shot morning and evening daily reflection reads a day-detail view needs into a
 * single [DayDetailData] flow.
 *
 * NiA domain-layer pattern: a use case combines reads from more than one repository so the ViewModel
 * receives one stream. Here both reads are on the same repository, but the use case still owns
 * assembling them into one domain model.
 */
class GetDayDetailUseCase(
    private val reflectionRepository: DailyReflectionRepository,
) {
    operator fun invoke(epochDay: Long): Flow<DayDetailData> = flow {
        emit(
            DayDetailData(
                morningReflection = reflectionRepository.getForDay(epochDay, TONE_MORNING),
                eveningReflection = reflectionRepository.getForDay(epochDay, TONE_EVENING),
            ),
        )
    }

    private companion object {
        const val TONE_MORNING = "morning"
        const val TONE_EVENING = "evening"
    }
}
