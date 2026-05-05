package com.mediasage.feature.bookmarks

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mediasage.domain.repository.EncouragementRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

private const val QUOTE_PREVIEW_LENGTH = 120

class BookmarksViewModel(
    private val encouragementRepository: EncouragementRepository
) : ViewModel() {

    private val _state = MutableStateFlow<BookmarksContract.UiState>(BookmarksContract.UiState.Loading)
    val state: StateFlow<BookmarksContract.UiState> = _state.asStateFlow()

    init {
        loadBookmarks()
    }

    fun onIntent(intent: BookmarksContract.Intent) {
        when (intent) {
            is BookmarksContract.Intent.ToggleBookmark -> {
                viewModelScope.launch { encouragementRepository.toggleBookmark(intent.articleUrl) }
            }
        }
    }

    private fun loadBookmarks() {
        viewModelScope.launch {
            encouragementRepository.observeBookmarked().collect { encouragements ->
                if (encouragements.isEmpty()) {
                    _state.value = BookmarksContract.UiState.Empty
                } else {
                    val items = encouragements.map { encouragement ->
                        BookmarkItem(
                            articleUrl = encouragement.articleUrl ?: "",
                            headlineTitle = encouragement.headlineTitle,
                            figureName = encouragement.figureName,
                            figureRole = encouragement.figureRole,
                            quotePreview = encouragement.quoteText.take(QUOTE_PREVIEW_LENGTH),
                            headlineImageUrl = encouragement.headlineImageUrl
                        )
                    }
                    _state.value = BookmarksContract.UiState.Success(items)
                }
            }
        }
    }
}
