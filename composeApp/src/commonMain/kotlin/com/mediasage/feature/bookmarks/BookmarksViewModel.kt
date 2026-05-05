package com.mediasage.feature.bookmarks

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mediasage.data.local.dao.EncouragementDao
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

private const val QUOTE_PREVIEW_LENGTH = 120

class BookmarksViewModel(
    private val encouragementDao: EncouragementDao
) : ViewModel() {

    private val _state = MutableStateFlow<BookmarksContract.UiState>(BookmarksContract.UiState.Loading)
    val state: StateFlow<BookmarksContract.UiState> = _state.asStateFlow()

    init {
        loadBookmarks()
    }

    fun onIntent(intent: BookmarksContract.Intent) {
        when (intent) {
            is BookmarksContract.Intent.ToggleBookmark -> {
                viewModelScope.launch { encouragementDao.toggleBookmark(intent.articleUrl) }
            }
        }
    }

    private fun loadBookmarks() {
        viewModelScope.launch {
            encouragementDao.observeBookmarked().collect { entities ->
                if (entities.isEmpty()) {
                    _state.value = BookmarksContract.UiState.Empty
                } else {
                    val items = entities.map { entity ->
                        BookmarkItem(
                            articleUrl = entity.articleUrl,
                            headlineTitle = entity.headlineTitle,
                            figureName = entity.figureName,
                            figureRole = entity.figureRole,
                            quotePreview = entity.quoteText.take(QUOTE_PREVIEW_LENGTH),
                            headlineImageUrl = entity.headlineImageUrl
                        )
                    }
                    _state.value = BookmarksContract.UiState.Success(items)
                }
            }
        }
    }
}
