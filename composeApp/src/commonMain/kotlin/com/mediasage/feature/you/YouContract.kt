package com.mediasage.feature.you

import kotlinx.datetime.DayOfWeek

object YouContract {

    data class DaySlot(
        val dayOfWeek: DayOfWeek,
        val isToday: Boolean,
        val assignedFigureName: String? = null,
        val assignedFigureImageUrl: String? = null,
    )

    enum class LensFilter { TODAY, HOPE, ANXIETY, LOVE, GRIEF, JUSTICE }

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
            val selectedLens: LensFilter = LensFilter.TODAY,
            val quoteCard: QuoteCard? = null,
        ) : UiState
    }

    sealed interface Intent {
        data class DaySlotTapped(val index: Int) : Intent
        data class LensSelected(val lens: LensFilter) : Intent
    }
}
