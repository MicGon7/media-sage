package com.mediasage.feature.history

object HistoryContract {

    sealed interface UiState {
        data object Loading : UiState
        data object Empty : UiState
        data class Success(val items: List<HistoryItem>) : UiState
    }

    sealed interface Intent {
        data class ToggleBookmark(val articleUrl: String) : Intent
    }

    sealed interface SideEffect
}

data class HistoryItem(
    val articleUrl: String,
    val headlineTitle: String,
    val figureName: String,
    val figureRole: String,
    val quotePreview: String,
    val headlineImageUrl: String?,
    val figureImageUrl: String? = null,
    val isBookmarked: Boolean = false
)
