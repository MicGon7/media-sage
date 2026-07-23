package com.mediasage.domain.usecase

import com.mediasage.domain.model.ReaderCalendarData
import com.mediasage.domain.repository.DailyReflectionRepository
import com.mediasage.domain.repository.DayAssignmentRepository
import com.mediasage.domain.repository.FigureRepository
import com.mediasage.domain.repository.QuoteRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

/**
 * Combines the repository streams that feed the Reader calendar into a single [ReaderCalendarData]
 * flow, scoped to the visible-month range (briefings and weekly assignments).
 *
 * This is the Now in Android domain-layer pattern: a use case exists specifically to combine and
 * transform data from multiple repositories so a ViewModel receives one stream instead of four.
 * Event handling (assigning reporters) is not part of this — that stays in the ViewModel, which
 * calls the repository directly.
 */
class GetReaderCalendarUseCase(
    private val figureRepository: FigureRepository,
    private val dayAssignmentRepository: DayAssignmentRepository,
    private val quoteRepository: QuoteRepository,
    private val reflectionRepository: DailyReflectionRepository,
) {
    operator fun invoke(
        monthStartEpochDay: Long,
        monthEndEpochDay: Long,
    ): Flow<ReaderCalendarData> =
        combine(
            figureRepository.observeAllFigures(),
            dayAssignmentRepository.observeAssignments(),
            quoteRepository.observeAllQuotes(),
            reflectionRepository.observeByEpochDayRange(monthStartEpochDay, monthEndEpochDay),
        ) { figures, assignments, quotes, briefingDays ->
            ReaderCalendarData(
                figures = figures,
                assignmentsByDayOfWeek = assignments,
                latestQuote = quotes.maxByOrNull { it.id },
                briefingByDay = briefingDays.associate { it.epochDay to it.figureId },
            )
        }
}
