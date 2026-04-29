package com.mediasage.feature.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mediasage.data.local.dao.EncouragementDao
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

private const val QUOTE_PREVIEW_LENGTH = 120

class HistoryViewModel(
    private val encouragementDao: EncouragementDao
) : ViewModel() {

    private val _state = MutableStateFlow<HistoryContract.UiState>(HistoryContract.UiState.Loading)
    val state: StateFlow<HistoryContract.UiState> = _state.asStateFlow()

    init {
        loadHistory()
    }

    fun onIntent(intent: HistoryContract.Intent) = Unit

    private fun loadHistory() {
        viewModelScope.launch {
            encouragementDao.getAll().collect { entities ->
                if (entities.isEmpty()) {
                    _state.value = HistoryContract.UiState.Empty
                } else {
                    val items = entities.map { entity ->
                        HistoryItem(
                            articleUrl = entity.articleUrl,
                            headlineTitle = entity.headlineTitle,
                            figureName = entity.figureName,
                            figureRole = entity.figureRole,
                            quotePreview = entity.quoteText.take(QUOTE_PREVIEW_LENGTH),
                            headlineImageUrl = entity.headlineImageUrl
                        )
                    }
                    _state.value = HistoryContract.UiState.Success(items)
                }
            }
        }
    }
}
