package com.mediasage.feature.you

object HistoryContract {

    enum class CalendarMode { WEEK, MONTH, YEAR }

    data class CalendarDay(
        val epochDay: Long,
        val label: String,
        val isToday: Boolean,
        val hasData: Boolean,
        val isFuture: Boolean = false,
        val figurePortraitUrl: String? = null,
        val figureName: String? = null,
    )

    data class ReflectionSummary(
        val scriptureReference: String,
        val scriptureText: String,
        val insight: String,
        val implication: String,
        val inspiration: String,
        val sources: List<String>,
    )

    data class EncouragementItem(
        val headlineTitle: String,
        val quoteText: String,
        val figureName: String,
        val figureRole: String,
        val figureImageUrl: String?,
        val articleUrl: String,
        val isBookmarked: Boolean,
    )

    data class DayDetail(
        val epochDay: Long,
        val reflection: ReflectionSummary?,
        val encouragements: List<EncouragementItem>,
        val figureName: String? = null,
        val figureImageUrl: String? = null,
    )

    enum class DayTab { BRIEFING, ARTICLES }

    sealed interface UiState {
        data object Loading : UiState
        data class Ready(
            val mode: CalendarMode = CalendarMode.WEEK,
            val selectedTab: DayTab = DayTab.BRIEFING,
            val calendarDays: List<CalendarDay> = emptyList(),
            val selectedEpochDay: Long? = null,
            val dayDetail: DayDetail? = null,
        ) : UiState
    }

    sealed interface Intent {
        data class SelectMode(val mode: CalendarMode) : Intent
        data class SelectDay(val epochDay: Long) : Intent
        data class SelectTab(val tab: DayTab) : Intent
        data object ClearSelection : Intent
        data class ToggleBookmark(val articleUrl: String) : Intent
    }
}
