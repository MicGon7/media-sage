package com.mediasage.feature.you

import com.mediasage.domain.model.Figure
import com.mediasage.domain.model.LensFilter
import kotlinx.datetime.DayOfWeek

object ReaderContract {

    data class DaySlot(
        val dayOfWeek: DayOfWeek,
        val epochDay: Long,
        val isToday: Boolean,
        val assignedFigureName: String? = null,
        val assignedFigureImageUrl: String? = null,
        val assignedLens: LensFilter? = null,
    )

    data class QuoteCard(
        val quoteText: String,
        val figureName: String,
        val figureRole: String,
        val figureImageUrl: String?,
        val figureId: Long,
    )

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

    sealed interface ActiveSheet {
        data class WeekSlotPicker(val dayOfWeek: Int) : ActiveSheet
        data class HistoryDetail(val detail: DayDetail) : ActiveSheet
    }

    sealed interface UiState {
        data class Ready(
            val weekSlots: List<DaySlot> = emptyList(),
            val quoteCard: QuoteCard? = null,
            val pickerFigures: List<Figure> = emptyList(),
            val calendarDays: List<CalendarDay> = emptyList(),
            val isCalendarExpanded: Boolean = false,
            val activeSheet: ActiveSheet? = null,
        ) : UiState
    }

    sealed interface Intent {
        data class DaySlotTapped(val index: Int) : Intent
        data object PickerDismissed : Intent
        data class FigureAssigned(val dayOfWeek: Int, val figureId: Long, val lens: LensFilter?) : Intent
        data class AssignmentCleared(val dayOfWeek: Int) : Intent
        data object ToggleCalendarExpanded : Intent
        data class MonthPageChanged(val year: Int, val month: Int) : Intent
        data class HistoryDayTapped(val epochDay: Long) : Intent
    }
}
