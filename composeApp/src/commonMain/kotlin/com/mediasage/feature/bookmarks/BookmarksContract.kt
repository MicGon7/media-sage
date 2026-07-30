package com.mediasage.feature.bookmarks

object BookmarksContract {

    sealed interface UiState {
        data object Loading : UiState
        data object Empty : UiState
        data class Success(val items: List<BookmarkItem>) : UiState
    }

    sealed interface Intent {
        data class ToggleBookmark(val articleUrl: String) : Intent
    }

    sealed interface SideEffect
}

data class BookmarkItem(
    val articleUrl: String,
    val headlineTitle: String,
    val figureName: String,
    val figureRole: String,
    val quotePreview: String,
    val headlineImageUrl: String?,
    val source: String = "",
    val category: String = "",
    val publishedAtLabel: String = ""
)
