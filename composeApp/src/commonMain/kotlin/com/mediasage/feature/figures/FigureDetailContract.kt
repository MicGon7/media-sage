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
            val isPinned: Boolean = false
        ) : UiState
        data class Error(val message: String) : UiState
    }

    sealed interface Intent {
        data object PinToHome : Intent
    }
}

data class FigureQuoteItem(
    val quoteText: String,
    val headlineTitle: String
)
