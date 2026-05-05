package com.mediasage.feature.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mediasage.domain.repository.EncouragementRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

private const val QUOTE_PREVIEW_LENGTH = 120

class HistoryViewModel(
    private val encouragementRepository: EncouragementRepository
) : ViewModel() {

    private val _state = MutableStateFlow<HistoryContract.UiState>(HistoryContract.UiState.Loading)
    val state: StateFlow<HistoryContract.UiState> = _state.asStateFlow()

    init {
        loadHistory()
    }

    fun onIntent(intent: HistoryContract.Intent) {
        when (intent) {
            is HistoryContract.Intent.ToggleBookmark -> {
                viewModelScope.launch { encouragementRepository.toggleBookmark(intent.articleUrl) }
            }
        }
    }

    private fun loadHistory() {
        viewModelScope.launch {
            encouragementRepository.observeAll().collect { encouragements ->
                if (encouragements.isEmpty()) {
                    _state.value = HistoryContract.UiState.Empty
                } else {
                    val items = encouragements.map { encouragement ->
                        HistoryItem(
                            articleUrl = encouragement.articleUrl ?: "",
                            headlineTitle = encouragement.headlineTitle,
                            figureName = encouragement.figureName,
                            figureRole = encouragement.figureRole,
                            quotePreview = encouragement.quoteText.take(QUOTE_PREVIEW_LENGTH),
                            headlineImageUrl = encouragement.headlineImageUrl,
                            isBookmarked = encouragement.bookmarked
                        )
                    }
                    _state.value = HistoryContract.UiState.Success(items)
                }
            }
        }
    }
}
