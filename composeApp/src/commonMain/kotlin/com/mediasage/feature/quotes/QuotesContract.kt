package com.mediasage.feature.quotes

object QuotesContract {

    data class QuoteItem(
        val quoteText: String,
        val isMemorized: Boolean = false,
    )

    data class FigureSection(
        val figureId: Long,
        val figureName: String,
        val figureImageUrl: String?,
        val quotes: List<QuoteItem>,
    )

    sealed interface UiState {
        data object Loading : UiState
        data class Success(
            val sections: List<FigureSection> = emptyList(),
        ) : UiState
    }

    sealed interface Intent {
        data class QuoteSelected(val figureId: Long, val quoteText: String) : Intent
    }
}
