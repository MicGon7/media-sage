package com.mediasage.domain.usecase

import com.mediasage.domain.model.BriefingLoadInputs
import com.mediasage.domain.repository.DailyReflectionRepository
import com.mediasage.domain.repository.DayAssignmentRepository
import com.mediasage.domain.repository.FigureRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

/**
 * Combines the day-assignment/reflection resolution signals with the live assignment and figure
 * roster streams into a single [BriefingLoadInputs] flow, so BriefingViewModel receives one stream
 * instead of combining four repository streams itself.
 *
 * NiA domain-layer pattern: a use case combines reads from more than one repository so the
 * ViewModel receives one stream. Event handling (resolving today's reporter, saving/reading the
 * reflection note) is not part of this — those stay direct repository calls in the ViewModel.
 */
class GetBriefingLoadInputsUseCase(
    private val dayAssignmentRepository: DayAssignmentRepository,
    private val dailyReflectionRepository: DailyReflectionRepository,
    private val figureRepository: FigureRepository,
) {
    operator fun invoke(): Flow<BriefingLoadInputs> =
        combine(
            dayAssignmentRepository.isResolved,
            dailyReflectionRepository.isResolved,
            dayAssignmentRepository.observeAssignments(),
            figureRepository.observeAllFigures(),
        ) { dayResolved, reflectionResolved, assignments, figures ->
            BriefingLoadInputs(dayResolved && reflectionResolved, assignments, figures)
        }
}
