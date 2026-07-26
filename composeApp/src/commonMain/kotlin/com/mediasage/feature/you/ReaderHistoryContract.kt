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
        val scriptureReference: String? = null,
        val scriptureText: String? = null,
    )

    sealed interface UiState {
        data class Ready(
            val todayEpochDay: Long = 0L,
            val earliestEpochDay: Long = 0L,
            val viewMode: ViewMode = ViewMode.LIST,
            val calendarMonths: List<List<CalendarDay>> = emptyList(),
            val listDays: List<ListDay> = emptyList(),
        ) : UiState
    }

    sealed interface Intent {
        data class ViewModeChanged(val viewMode: ViewMode) : Intent
    }
}
