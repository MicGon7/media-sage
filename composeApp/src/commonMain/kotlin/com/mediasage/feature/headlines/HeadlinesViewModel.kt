package com.mediasage.feature.headlines

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mediasage.data.repository.epochMillis
import com.mediasage.domain.repository.HeadlineRepository
import com.mediasage.ui.formatHeadlineDate
import com.mediasage.ui.toErrorType
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

class HeadlinesViewModel(
    private val headlineRepository: HeadlineRepository
) : ViewModel() {

    private val _state = MutableStateFlow<HeadlinesContract.UiState>(
        HeadlinesContract.UiState.Loading(todayLabel())
    )
    val state: StateFlow<HeadlinesContract.UiState> = _state.asStateFlow()

    private val _sideEffects = Channel<HeadlinesContract.SideEffect>(Channel.BUFFERED)
    val sideEffects = _sideEffects.receiveAsFlow()

    init {
        collectHeadlines()
        fetchHeadlines()
    }

    fun onIntent(intent: HeadlinesContract.Intent) {
        when (intent) {
            is HeadlinesContract.Intent.Load -> retryLoad()
            is HeadlinesContract.Intent.Refresh -> refreshHeadlines()
            is HeadlinesContract.Intent.HeadlineClicked -> { /* handled via navigation callback */ }
        }
    }

    private fun retryLoad() {
        _state.value = HeadlinesContract.UiState.Loading(todayLabel())
        fetchHeadlines()
    }

    private fun collectHeadlines() {
        viewModelScope.launch {
            headlineRepository.observeHeadlines()
                .collect { headlines ->
                    if (headlines.isNotEmpty()) {
                        val current = _state.value
                        val isRefreshing = current is HeadlinesContract.UiState.Success && current.isRefreshing
                        _state.value = HeadlinesContract.UiState.Success(
                            headlines = headlines.map { it.toItem() },
                            todayLabel = todayLabel(),
                            isRefreshing = isRefreshing
                        )
                    }
                }
        }
    }

    private fun fetchHeadlines() {
        viewModelScope.launch {
            try {
                headlineRepository.refreshHeadlines()
            } catch (e: Exception) {
                if (_state.value is HeadlinesContract.UiState.Loading) {
                    _state.value = HeadlinesContract.UiState.Error(e.toErrorType())
                }
            }
        }
    }

    private fun refreshHeadlines() {
        viewModelScope.launch {
            val current = _state.value
            if (current is HeadlinesContract.UiState.Success) {
                _state.value = current.copy(isRefreshing = true)
            }
            runCatching { headlineRepository.refreshHeadlines() }
                .onFailure { e ->
                    _sideEffects.send(HeadlinesContract.SideEffect.ShowError(e.message ?: "Failed to refresh"))
                }
            val updated = _state.value as? HeadlinesContract.UiState.Success ?: return@launch
            _state.value = updated.copy(isRefreshing = false)
        }
    }

    private fun todayLabel(): String {
        val date = Instant.fromEpochMilliseconds(epochMillis())
            .toLocalDateTime(TimeZone.currentSystemDefault()).date
        val day = date.dayOfWeek.name.lowercase().replaceFirstChar { it.uppercase() }
        val month = date.month.name.lowercase().replaceFirstChar { it.uppercase() }
        return "$day, $month ${date.day}, ${date.year}"
    }
}

private fun com.mediasage.domain.model.Headline.toItem() = HeadlineItem(
    id = id,
    articleUrl = url,
    title = title,
    source = source,
    category = category,
    snippet = snippet.orEmpty(),
    imageUrl = imageUrl,
    publishedAtLabel = formatHeadlineDate(publishedAt)
)
