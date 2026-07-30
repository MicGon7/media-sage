package com.mediasage.feature.headlines

import com.mediasage.ui.ErrorType

object HeadlinesContract {

    sealed interface UiState {
        data class Loading(val todayLabel: String) : UiState
        data class Success(
            val headlines: List<HeadlineItem>,
            val todayLabel: String = "",
            val isRefreshing: Boolean = false
        ) : UiState
        data class Error(val errorType: ErrorType) : UiState
    }

    sealed interface Intent {
        data object Load : Intent
        data object Refresh : Intent
        data class HeadlineClicked(val articleUrl: String) : Intent
        data class ToggleBookmark(val articleUrl: String) : Intent
    }

    sealed interface SideEffect {
        data class NavigateToDetail(val articleUrl: String) : SideEffect
        data class ShowError(val message: String) : SideEffect
    }
}

data class HeadlineItem(
    val id: Long,
    val articleUrl: String,
    val title: String,
    val source: String,
    val category: String = "",
    val snippet: String = "",
    val imageUrl: String?,
    val publishedAtLabel: String = "",
    val isRead: Boolean = false,
    val figureName: String? = null,
    val figureRole: String? = null,
    val quotePreview: String? = null,
    val isBookmarked: Boolean = false
)
