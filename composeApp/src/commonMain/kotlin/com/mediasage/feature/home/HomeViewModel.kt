package com.mediasage.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mediasage.domain.repository.HeadlineRepository
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import com.mediasage.ui.toErrorType
import kotlinx.coroutines.launch

class HomeViewModel(
    private val headlineRepository: HeadlineRepository
) : ViewModel() {

    private val _state = MutableStateFlow<HomeContract.UiState>(HomeContract.UiState.Loading)
    val state: StateFlow<HomeContract.UiState> = _state.asStateFlow()

    private val _sideEffects = Channel<HomeContract.SideEffect>()
    val sideEffects = _sideEffects.receiveAsFlow()

    init {
        collectHeadlines()
        fetchHeadlines()
    }

    fun onIntent(intent: HomeContract.Intent) {
        when (intent) {
            is HomeContract.Intent.LoadHeadlines -> retryLoad()
            is HomeContract.Intent.RefreshHeadlines -> refreshHeadlines()
            is HomeContract.Intent.HeadlineClicked -> { /* Handled via navigation callback */ }
        }
    }

    /** User tapped retry after an error. */
    private fun retryLoad() {
        _state.value = HomeContract.UiState.Loading
        fetchHeadlines()
    }

    /** Collects headlines from Room. Any DB change automatically updates the UI. */
    private fun collectHeadlines() {
        viewModelScope.launch {
            headlineRepository.getHeadlines().collect { headlines ->
                if (headlines.isNotEmpty()) {
                    _state.value = HomeContract.UiState.Success(
                        headlines = headlines.map { it.toItem() }
                    )
                } else if (_state.value !is HomeContract.UiState.Loading) {
                    _state.value = HomeContract.UiState.Empty
                }
            }
        }
    }

    /** Fetches fresh headlines from the server and saves to Room. */
    private fun fetchHeadlines() {
        viewModelScope.launch {
            try {
                headlineRepository.refreshHeadlines()
            } catch (e: Exception) {
                if (_state.value is HomeContract.UiState.Loading) {
                    _state.value = HomeContract.UiState.Error(e.toErrorType())
                }
            } finally {
                if (_state.value is HomeContract.UiState.Loading) {
                    _state.value = HomeContract.UiState.Empty
                }
            }
        }
    }

    /** User-initiated pull-to-refresh. */
    private fun refreshHeadlines() {
        val current = _state.value
        if (current is HomeContract.UiState.Success) {
            _state.value = current.copy(isRefreshing = true)
        }
        viewModelScope.launch {
            try {
                headlineRepository.refreshHeadlines()
            } catch (e: Exception) {
                _sideEffects.send(
                    HomeContract.SideEffect.ShowError(
                        e.message ?: "Failed to refresh headlines"
                    )
                )
            } finally {
                val updated = _state.value
                if (updated is HomeContract.UiState.Success) {
                    _state.value = updated.copy(isRefreshing = false)
                }
            }
        }
    }
}

private fun com.mediasage.domain.model.Headline.toItem() = HeadlineItem(
    id = id,
    articleUrl = url,
    title = title,
    source = source,
    imageUrl = imageUrl,
    publishedAt = publishedAt
)
