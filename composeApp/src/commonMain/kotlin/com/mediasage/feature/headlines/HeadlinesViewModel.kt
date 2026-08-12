package com.mediasage.feature.headlines

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mediasage.data.HeadlineCategoryPreferencesRepository
import com.mediasage.data.repository.epochMillis
import com.mediasage.domain.model.HeadlineFeedEntry
import com.mediasage.domain.repository.EncouragementRepository
import com.mediasage.domain.repository.HeadlineRepository
import com.mediasage.domain.usecase.GetHeadlinesFeedUseCase
import com.mediasage.ui.formatHeadlineDate
import com.mediasage.ui.toErrorType
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

class HeadlinesViewModel(
    private val headlineRepository: HeadlineRepository,
    private val encouragementRepository: EncouragementRepository,
    private val getHeadlinesFeed: GetHeadlinesFeedUseCase,
    private val categoryPreferencesRepository: HeadlineCategoryPreferencesRepository,
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
            is HeadlinesContract.Intent.ToggleBookmark -> {
                viewModelScope.launch { encouragementRepository.toggleBookmark(intent.articleUrl) }
            }
            is HeadlinesContract.Intent.CategoryToggled -> {
                viewModelScope.launch { categoryPreferencesRepository.toggleCategory(intent.category) }
            }
        }
    }

    private fun retryLoad() {
        _state.value = HeadlinesContract.UiState.Loading(todayLabel())
        fetchHeadlines()
    }

    private fun collectHeadlines() {
        viewModelScope.launch {
            combine(
                getHeadlinesFeed(),
                categoryPreferencesRepository.selectedCategories,
            ) { entries, selectedCategories -> entries to selectedCategories }
                .collect { (entries, selectedCategories) ->
                    if (entries.isNotEmpty()) {
                        val current = _state.value
                        val isRefreshing = current is HeadlinesContract.UiState.Success && current.isRefreshing
                        val filtered = if (selectedCategories.isEmpty()) {
                            entries
                        } else {
                            entries.filter { it.headline.category in selectedCategories }
                        }
                        _state.value = HeadlinesContract.UiState.Success(
                            headlines = filtered.map { it.toItem() },
                            selectedCategories = selectedCategories,
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

private fun HeadlineFeedEntry.toItem() = HeadlineItem(
    id = headline.id,
    articleUrl = headline.url,
    title = headline.title,
    source = headline.source,
    category = headline.category,
    snippet = headline.snippet.orEmpty(),
    imageUrl = headline.imageUrl,
    publishedAtLabel = formatHeadlineDate(headline.publishedAt),
    isRead = headline.isRead,
    figureName = figureName,
    figureRole = figureRole,
    figureImageUrl = figureImageUrl,
    quotePreview = quotePreview,
    isBookmarked = isBookmarked
)
