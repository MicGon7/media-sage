package com.mediasage.feature.you

import com.mediasage.domain.model.Figure
import com.mediasage.domain.model.LensFilter
import kotlinx.datetime.DayOfWeek

object ReaderContract {

    data class DaySlot(
        val dayOfWeek: DayOfWeek,
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
        val overrideFigureId: Long? = null,
    )

    sealed interface UiState {
        data class Ready(
            val weekSlots: List<DaySlot> = emptyList(),
            val quoteCard: QuoteCard? = null,
            val pickerOpenForDay: Int? = null,
            val pickerOpenForEpochDay: Long? = null,
            val pickerFigures: List<Figure> = emptyList(),
            val calendarDays: List<CalendarDay> = emptyList(),
        ) : UiState
    }

    sealed interface Intent {
        data class DaySlotTapped(val index: Int) : Intent
        data object PickerDismissed : Intent
        data class FigureAssigned(val dayOfWeek: Int, val figureId: Long, val lens: LensFilter?) : Intent
        data class AssignmentCleared(val dayOfWeek: Int) : Intent
        data class SelectFutureDay(val epochDay: Long) : Intent
        data class AssignOverride(val epochDay: Long, val figureId: Long) : Intent
        data class ClearOverride(val epochDay: Long) : Intent
    }
}
