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

    sealed interface ActiveSheet {
        data class WeekSlotPicker(val dayOfWeek: Int) : ActiveSheet
    }

    sealed interface UiState {
        data class Ready(
            val weekSlots: List<DaySlot> = emptyList(),
            val quoteCard: QuoteCard? = null,
            val pickerFigures: List<Figure> = emptyList(),
            val activeSheet: ActiveSheet? = null,
        ) : UiState
    }

    sealed interface Intent {
        data class DaySlotTapped(val index: Int) : Intent
        data object PickerDismissed : Intent
        data class FigureAssigned(val dayOfWeek: Int, val figureId: Long, val lens: LensFilter?) : Intent
        data class AssignmentCleared(val dayOfWeek: Int) : Intent
    }
}
