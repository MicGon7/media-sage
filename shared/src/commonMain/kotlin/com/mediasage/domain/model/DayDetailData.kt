package com.mediasage.domain.model

/**
 * UI-agnostic detail for a single day: the morning and evening reflections that ran (each may be
 * absent if that tone hasn't generated yet) and the encouragements saved that day.
 *
 * Produced by `GetDayDetailUseCase`. Mapping this into displayable summaries and article rows is
 * the UI layer's responsibility, not the domain's.
 */
data class DayDetailData(
    val morningReflection: DailyReflection?,
    val eveningReflection: DailyReflection?,
    val encouragements: List<Encouragement>,
)
