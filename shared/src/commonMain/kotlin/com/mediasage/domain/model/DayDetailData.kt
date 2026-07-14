package com.mediasage.domain.model

/**
 * UI-agnostic detail for a single day: the daily reflection that ran (if any) and the encouragements
 * saved that day.
 *
 * Produced by `ObserveDayDetailUseCase`. Mapping this into displayable summaries and article rows is
 * the UI layer's responsibility, not the domain's.
 */
data class DayDetailData(
    val reflection: DailyReflection?,
    val encouragements: List<Encouragement>,
)
