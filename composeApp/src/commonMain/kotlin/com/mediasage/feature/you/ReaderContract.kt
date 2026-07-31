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
    )

    sealed interface DayLabel {
        data object Yesterday : DayLabel
        data class Text(val value: String) : DayLabel
    }

    data class PastBriefingCard(
        val epochDay: Long,
        val figureName: String,
        val figureImageUrl: String?,
        val inspiration: String,
        val dayLabel: DayLabel,
    )

    sealed interface ActiveSheet {
        data class WeekSlotPicker(val dayOfWeek: Int) : ActiveSheet
    }

    /** Awaiting user confirmation to reassign a locked-in day to a different figure. */
    data class PendingReassignment(
        val dayOfWeek: Int,
        val figureId: Long,
        val lens: LensFilter?,
        val currentFigureName: String,
        val newFigureName: String,
        val nextWeekdayLabel: String,
    )

    sealed interface UiState {
        data class Ready(
            val weekSlots: List<DaySlot> = emptyList(),
            val quoteCard: QuoteCard? = null,
            val pastBriefings: List<PastBriefingCard> = emptyList(),
            val hasMorePastBriefings: Boolean = false,
            val pickerFigures: List<Figure> = emptyList(),
            val activeSheet: ActiveSheet? = null,
            val pendingReassignment: PendingReassignment? = null,
            val userDisplayName: String? = null,
        ) : UiState
    }

    sealed interface Intent {
        data class DaySlotTapped(val index: Int) : Intent
        data object PickerDismissed : Intent
        data class FigureAssigned(val dayOfWeek: Int, val figureId: Long, val lens: LensFilter?) : Intent
        data class AssignmentCleared(val dayOfWeek: Int) : Intent
        data object ConfirmReassignment : Intent
        data object CancelReassignment : Intent
    }
}
