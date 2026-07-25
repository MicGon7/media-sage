package com.mediasage.feature.you

object ReaderHistoryContract {

    enum class ViewMode { CALENDAR, LIST }

    data class CalendarDay(
        val epochDay: Long,
        val dateNumber: Int,
        val isToday: Boolean,
        val isFuture: Boolean,
        val hasData: Boolean,
        val figurePortraitUrl: String?,
        val figureName: String?,
    )

    data class ListDay(
        val epochDay: Long,
        val figurePortraitUrl: String?,
        val figureName: String,
    )

    sealed interface UiState {
        data class Ready(
            val todayEpochDay: Long = 0L,
            val earliestEpochDay: Long = 0L,
            val viewMode: ViewMode = ViewMode.CALENDAR,
            val calendarDays: List<CalendarDay> = emptyList(),
            val listDays: List<ListDay> = emptyList(),
        ) : UiState
    }

    sealed interface Intent {
        data class MonthPageChanged(val year: Int, val month: Int) : Intent
        data class ViewModeChanged(val viewMode: ViewMode) : Intent
    }
}
