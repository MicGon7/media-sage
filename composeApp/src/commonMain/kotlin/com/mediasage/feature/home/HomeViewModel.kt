package com.mediasage.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mediasage.data.repository.epochMillis
import com.mediasage.domain.repository.DailyReflectionRepository
import com.mediasage.domain.repository.FigureRepository
import com.mediasage.domain.repository.HeadlineRepository
import com.mediasage.domain.repository.PinnedFigureRepository
import com.mediasage.ui.toErrorType
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

class HomeViewModel(
    private val headlineRepository: HeadlineRepository,
    private val pinnedFigureRepository: PinnedFigureRepository,
    private val dailyReflectionRepository: DailyReflectionRepository,
    private val figureRepository: FigureRepository
) : ViewModel() {

    private val _state = MutableStateFlow<HomeContract.UiState>(HomeContract.UiState.Loading)
    val state: StateFlow<HomeContract.UiState> = _state.asStateFlow()

    private val _sideEffects = Channel<HomeContract.SideEffect>()
    val sideEffects = _sideEffects.receiveAsFlow()

    init {
        collectHeadlines()
        fetchHeadlines()
        loadBriefingCard()
    }

    fun onIntent(intent: HomeContract.Intent) {
        when (intent) {
            is HomeContract.Intent.LoadHeadlines -> retryLoad()
            is HomeContract.Intent.RefreshHeadlines -> refreshHeadlines()
            is HomeContract.Intent.HeadlineClicked -> { /* Handled via navigation callback */ }
        }
    }

    private fun retryLoad() {
        _state.value = HomeContract.UiState.Loading
        fetchHeadlines()
    }

    private fun collectHeadlines() {
        viewModelScope.launch {
            headlineRepository.getHeadlines().collect { headlines ->
                if (headlines.isNotEmpty()) {
                    val current = _state.value
                    val card = if (current is HomeContract.UiState.Success) current.briefingCard
                               else HomeContract.BriefingCardState.Hidden
                    _state.value = HomeContract.UiState.Success(
                        headlines = headlines.map { it.toItem() },
                        briefingCard = card
                    )
                } else if (_state.value !is HomeContract.UiState.Loading) {
                    _state.value = HomeContract.UiState.Empty
                }
            }
        }
    }

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
                    HomeContract.SideEffect.ShowError(e.message ?: "Failed to refresh headlines")
                )
            } finally {
                val updated = _state.value
                if (updated is HomeContract.UiState.Success) {
                    _state.value = updated.copy(isRefreshing = false)
                }
            }
        }
    }

    private fun loadBriefingCard() {
        viewModelScope.launch {
            pinnedFigureRepository.observePinnedFigureId().collect { figureId ->
                if (figureId == null) {
                    updateBriefingCard(HomeContract.BriefingCardState.Hidden)
                    return@collect
                }
                updateBriefingCard(HomeContract.BriefingCardState.Loading)
                try {
                    val figure = figureRepository.getFigureById(figureId) ?: return@collect
                    val tone = currentTone()
                    val headlines = currentHeadlineTitles()
                    val reflection = dailyReflectionRepository.getOrFetch(
                        figureId = figureId,
                        figureName = figure.name,
                        headlines = headlines,
                        tone = tone
                    )
                    updateBriefingCard(
                        HomeContract.BriefingCardState.Ready(
                            figureId = figureId,
                            figureName = figure.name,
                            figureImageUrl = figure.portraitUrl,
                            scriptureReference = reflection.scriptureReference,
                            scriptureText = reflection.scriptureText,
                            reflection = reflection.reflection,
                            sources = reflection.sources,
                            tone = reflection.tone
                        )
                    )
                } catch (e: Exception) {
                    updateBriefingCard(HomeContract.BriefingCardState.Hidden)
                }
            }
        }
    }

    private fun updateBriefingCard(card: HomeContract.BriefingCardState) {
        val current = _state.value
        if (current is HomeContract.UiState.Success) {
            _state.value = current.copy(briefingCard = card)
        }
    }

    private fun currentHeadlineTitles(): List<String> {
        val current = _state.value
        return if (current is HomeContract.UiState.Success) {
            current.headlines.map { it.title }
        } else emptyList()
    }

    private fun currentTone(): String {
        val hourUtc = (epochMillis() % 86400000L / 3600000L).toInt()
        return if (hourUtc < 12) "morning" else "evening"
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
