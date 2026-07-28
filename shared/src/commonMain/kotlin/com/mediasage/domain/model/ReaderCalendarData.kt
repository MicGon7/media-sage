package com.mediasage.domain.model

/**
 * UI-agnostic calendar material for the Reader screen, combined from multiple repositories.
 *
 * Produced by `GetReaderCalendarUseCase`. Holds the domain data the Reader calendar needs
 * (figures, weekly assignments, per-day briefing reporters, and the user's memorized quote);
 * turning it into displayable week slots and day cells is the UI layer's job, not the domain's.
 */
data class ReaderCalendarData(
    val figures: List<Figure>,
    val assignmentsByDayOfWeek: Map<Int, DayAssignment>,
    val memorizedQuote: Quote?,
    val briefingByDay: Map<Long, BriefingDay>,
)
