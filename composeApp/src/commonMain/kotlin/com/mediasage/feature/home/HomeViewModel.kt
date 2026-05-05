package com.mediasage.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mediasage.data.repository.epochMillis
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import com.mediasage.domain.repository.DailyReflectionRepository
import com.mediasage.domain.repository.FigureRepository
import com.mediasage.domain.repository.HeadlineRepository
import com.mediasage.domain.repository.PinnedFigureRepository
import com.mediasage.ui.toErrorType
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
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

    // Preserved independently so the card survives headline refresh cycles
    private var lastBriefingCard: HomeContract.BriefingCardState = HomeContract.BriefingCardState.Hidden

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
                    _state.value = HomeContract.UiState.Success(
                        headlines = headlines.map { it.toItem() },
                        briefingCard = lastBriefingCard
                    )
                } else {
                    val current = _state.value
                    val isRefreshing = current is HomeContract.UiState.Success && current.isRefreshing
                    if (current !is HomeContract.UiState.Loading && !isRefreshing) {
                        _state.value = HomeContract.UiState.Empty
                    }
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
            combine(
                pinnedFigureRepository.observePinnedFigureId(),
                figureRepository.getAllFigures()
            ) { pinnedId, figures -> Pair(pinnedId, figures) }
                .distinctUntilChanged()
                .collect { (figureId, figures) ->
                if (figureId == null) {
                    val firstFigure = figures.firstOrNull()
                    if (firstFigure != null) {
                        pinnedFigureRepository.setPinnedFigureId(firstFigure.id)
                    } else {
                        updateBriefingCard(HomeContract.BriefingCardState.Hidden)
                    }
                    return@collect
                }
                updateBriefingCard(HomeContract.BriefingCardState.Loading)
                try {
                    val figure = figureRepository.getFigureById(figureId) ?: return@collect
                    val tone = currentTone()
                    // Read from Room directly — available even before network refresh completes
                    val headlines = headlineRepository.getHeadlines().first().map { it.title }
                    val reflection = dailyReflectionRepository.getOrFetch(
                        figureId = figure.serverId.takeIf { it > 0 } ?: figureId,
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
        lastBriefingCard = card
        val current = _state.value
        if (current is HomeContract.UiState.Success) {
            _state.value = current.copy(briefingCard = card)
        }
    }

    private fun currentTone(): String {
        val hour = Instant.fromEpochMilliseconds(epochMillis())
            .toLocalDateTime(TimeZone.currentSystemDefault()).hour
        return if (hour < 12) "morning" else "evening"
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
