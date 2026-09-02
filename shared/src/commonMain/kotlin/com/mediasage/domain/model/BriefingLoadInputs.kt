package com.mediasage.domain.model

/**
 * The resolution-gated inputs [com.mediasage.domain.usecase.GetBriefingLoadInputsUseCase] combines —
 * everything BriefingViewModel needs to decide whether today's assignment is safe to resolve yet.
 */
data class BriefingLoadInputs(
    val isResolved: Boolean,
    val assignments: Map<Int, DayAssignment>,
    val figures: List<Figure>,
)
