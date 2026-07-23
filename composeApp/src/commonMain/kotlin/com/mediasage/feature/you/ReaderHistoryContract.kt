package com.mediasage.feature.you

object ReaderHistoryContract {

    data class CalendarDay(
        val epochDay: Long,
        val dateNumber: Int,
        val isToday: Boolean,
        val isFuture: Boolean,
        val hasData: Boolean,
        val figurePortraitUrl: String?,
        val figureName: String?,
    )

    sealed interface UiState {
        data class Ready(
            val todayEpochDay: Long = 0L,
            val calendarDays: List<CalendarDay> = emptyList(),
        ) : UiState
    }

    sealed interface Intent {
        data class MonthPageChanged(val year: Int, val month: Int) : Intent
    }
}
