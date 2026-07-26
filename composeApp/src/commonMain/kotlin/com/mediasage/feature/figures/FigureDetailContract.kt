package com.mediasage.feature.figures

object FigureDetailContract {

    sealed interface UiState {
        data object Loading : UiState
        data class Success(
            val figureName: String,
            val figureRole: String,
            val figureImageUrl: String?,
            val bio: String?,
            val quotes: List<FigureQuoteItem>,
            val isPinned: Boolean = false,
            val pendingReassignment: PendingReassignment? = null,
        ) : UiState
        data class Error(val message: String) : UiState
    }

    /** Awaiting user confirmation to reassign today's weekday to a different figure. */
    data class PendingReassignment(
        val todayOrdinal: Int,
        val currentFigureName: String,
        val newFigureName: String,
        val nextWeekdayLabel: String,
    )

    sealed interface Intent {
        data object PinToHome : Intent
        data object ConfirmReassignment : Intent
        data object CancelReassignment : Intent
    }
}

data class FigureQuoteItem(
    val quoteText: String,
    val headlineTitle: String
)
