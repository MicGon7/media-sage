package com.mediasage.domain.usecase

import com.mediasage.domain.model.CalendarData
import com.mediasage.domain.repository.DailyReflectionRepository
import com.mediasage.domain.repository.DayAssignmentRepository
import com.mediasage.domain.repository.EncouragementRepository
import com.mediasage.domain.repository.FigureRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

/**
 * Combines the four repository streams that feed the calendar into a single [CalendarData] flow.
 *
 * This is the Now in Android domain-layer pattern: a use case exists specifically to combine and
 * transform data from multiple repositories so a ViewModel receives one stream instead of four.
 * Event handling (bookmarks, day selection) is not part of this — those stay in the ViewModel,
 * which calls repositories directly.
 */
class ObserveCalendarDataUseCase(
    private val figureRepository: FigureRepository,
    private val dayAssignmentRepository: DayAssignmentRepository,
    private val encouragementRepository: EncouragementRepository,
    private val reflectionRepository: DailyReflectionRepository,
) {
    operator fun invoke(yearStartEpochDay: Long, yearEndEpochDay: Long): Flow<CalendarData> =
        combine(
            figureRepository.observeAllFigures(),
            dayAssignmentRepository.observeAssignments(),
            encouragementRepository.observeActiveEpochDays(),
            reflectionRepository.observeByEpochDayRange(yearStartEpochDay, yearEndEpochDay),
        ) { figures, assignments, activeEncouragementDays, briefingDays ->
            CalendarData(
                figuresById = figures.associateBy { it.id },
                assignmentsByDayOfWeek = assignments,
                activeDays = activeEncouragementDays + briefingDays.map { it.epochDay },
                briefingByDay = briefingDays.associate { it.epochDay to it.figureId },
            )
        }
}
