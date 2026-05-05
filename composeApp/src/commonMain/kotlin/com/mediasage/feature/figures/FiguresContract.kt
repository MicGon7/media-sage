package com.mediasage.feature.figures

object FiguresContract {

    sealed interface UiState {
        data object Loading : UiState
        data class Success(
            val figures: List<VoiceFigureItem>,
            val searchQuery: String = ""
        ) : UiState
    }

    sealed interface Intent {
        data object LoadFigures : Intent
        data class FigureClicked(val figureId: Long) : Intent
        data class SearchQueryChanged(val query: String) : Intent
    }
}

data class VoiceFigureItem(
    val id: Long,
    val name: String,
    val role: String,
    val imageUrl: String?,
    val quoteCount: Int = 0,
    val isPinned: Boolean = false
)
