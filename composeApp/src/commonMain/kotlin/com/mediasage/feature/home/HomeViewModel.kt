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
import com.mediasage.ui.ErrorType
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

    private val _sideEffects = Channel<HomeContract.SideEffect>(Channel.BUFFERED)
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
            headlineRepository.observeHeadlines()
                .collect { headlines ->
                    if (headlines.isNotEmpty()) {
                        val current = _state.value
                        val isRefreshing = current is HomeContract.UiState.Success && current.isRefreshing
                        _state.value = HomeContract.UiState.Success(
                            headlines = headlines.map { it.toItem() },
                            briefingCard = lastBriefingCard,
                            isRefreshing = isRefreshing,
                            todayLabel = todayLabel()
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
                if (_state.value is HomeContract.UiState.Loading) {
                    _state.value = HomeContract.UiState.Error(e.toErrorType())
                }
            }
        }
    }

    private fun refreshHeadlines() {
        viewModelScope.launch {
            val current = _state.value
            if (current is HomeContract.UiState.Success) {
                _state.value = current.copy(isRefreshing = true)
            }
            runCatching { headlineRepository.refreshHeadlines() }
                .onSuccess {
                    val pinnedId = pinnedFigureRepository.observePinnedFigureId().first()
                    if (pinnedId != null) fetchAndUpdateBriefingCard(pinnedId)
                }
                .onFailure { e ->
                    _sideEffects.send(HomeContract.SideEffect.ShowError(e.message ?: "Failed to refresh headlines"))
                }
            val updated = _state.value as? HomeContract.UiState.Success ?: return@launch
            _state.value = updated.copy(isRefreshing = false)
        }
    }

    private suspend fun fetchAndUpdateBriefingCard(figureId: Long) {
        val figure = figureRepository.getFigureById(figureId) ?: return
        val tone = currentTone()
        val headlines = headlineRepository.observeHeadlines().first().map { it.title }
        updateBriefingCard(HomeContract.BriefingCardState.Loading)
        runCatching {
            dailyReflectionRepository.getOrFetch(
                figureId = figure.serverId.takeIf { it > 0 } ?: figureId,
                figureName = figure.name,
                headlines = headlines,
                tone = tone
            )
        }.onSuccess { reflection ->
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
        }.onFailure {
            updateBriefingCard(HomeContract.BriefingCardState.Hidden)
        }
    }

    private fun loadBriefingCard() {
        viewModelScope.launch {
            combine(
                pinnedFigureRepository.observePinnedFigureId(),
                figureRepository.observeAllFigures()
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
                fetchAndUpdateBriefingCard(figureId)
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
        return if (hour < 17) "morning" else "evening"
    }

    private fun todayLabel(): String {
        val date = Instant.fromEpochMilliseconds(epochMillis())
            .toLocalDateTime(TimeZone.currentSystemDefault()).date
        val day = date.dayOfWeek.name.lowercase().replaceFirstChar { it.uppercase() }
        val month = date.month.name.lowercase().replaceFirstChar { it.uppercase() }
        return "$day, $month ${date.dayOfMonth}, ${date.year}"
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
