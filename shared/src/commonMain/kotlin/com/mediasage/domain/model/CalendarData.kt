package com.mediasage.domain.model

/**
 * UI-agnostic calendar data combined from multiple repositories.
 *
 * Produced by `ObserveCalendarDataUseCase`. Holds the domain material a calendar view needs;
 * turning it into displayable day cells is the UI layer's responsibility, not the domain's.
 */
data class CalendarData(
    val figuresById: Map<Long, Figure>,
    val assignmentsByDayOfWeek: Map<Int, DayAssignment>,
    val activeDays: Set<Long>,
    val briefingByDay: Map<Long, Long>,
)
