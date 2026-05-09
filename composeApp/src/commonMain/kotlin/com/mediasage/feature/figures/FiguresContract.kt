package com.mediasage.feature.figures

object FiguresContract {

    sealed interface UiState {
        data object Loading : UiState
        data class Success(
            val figures: List<VoiceFigureItem>,
            val searchQuery: String = "",
            val isRefreshing: Boolean = false
        ) : UiState
    }

    sealed interface Intent {
        data object LoadFigures : Intent
        data object Refresh : Intent
        data class FigureClicked(val figureId: Long) : Intent
        data class SearchQueryChanged(val query: String) : Intent
    }

    sealed interface SideEffect {
        data class ShowError(val message: String) : SideEffect
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
