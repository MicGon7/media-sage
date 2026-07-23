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

    data class ReflectionSummary(
        val scriptureReference: String,
        val scriptureText: String,
        val insight: String,
        val implication: String,
        val inspiration: String,
        val sources: List<String>,
    )

    data class ArticleItem(
        val headlineTitle: String,
        val quoteText: String,
        val figureName: String,
        val figureRole: String,
        val figureImageUrl: String?,
        val articleUrl: String,
    )

    data class DayDetail(
        val epochDay: Long,
        val reflection: ReflectionSummary?,
        val articles: List<ArticleItem>,
        val figureName: String?,
        val figureImageUrl: String?,
    )

    sealed interface UiState {
        data class Ready(
            val todayEpochDay: Long = 0L,
            val calendarDays: List<CalendarDay> = emptyList(),
            val activeDetail: DayDetail? = null,
        ) : UiState
    }

    sealed interface Intent {
        data class MonthPageChanged(val year: Int, val month: Int) : Intent
        data class DayTapped(val epochDay: Long) : Intent
        data object DetailDismissed : Intent
    }
}
