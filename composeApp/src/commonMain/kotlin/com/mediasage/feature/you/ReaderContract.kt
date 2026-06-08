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
    )

    data class QuoteCard(
        val quoteText: String,
        val figureName: String,
        val figureRole: String,
        val figureImageUrl: String?,
        val figureId: Long,
    )

    sealed interface UiState {
        data class Ready(
            val weekSlots: List<DaySlot> = emptyList(),
            val selectedLens: LensFilter = LensFilter.NEWS,
            val quoteCard: QuoteCard? = null,
            val pickerOpenForDay: Int? = null,
            val pickerFigures: List<Figure> = emptyList(),
        ) : UiState
    }

    sealed interface Intent {
        data class DaySlotTapped(val index: Int) : Intent
        data class LensSelected(val lens: LensFilter) : Intent
        data object PickerDismissed : Intent
        data class FigureAssigned(val dayOfWeek: Int, val figureId: Long) : Intent
        data class AssignmentCleared(val dayOfWeek: Int) : Intent
    }
}
