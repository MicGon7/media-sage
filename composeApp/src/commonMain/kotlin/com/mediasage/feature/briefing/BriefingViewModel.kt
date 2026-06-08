package com.mediasage.feature.briefing

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mediasage.data.repository.epochMillis
import com.mediasage.domain.model.LensFilter
import com.mediasage.domain.repository.DailyReflectionRepository
import com.mediasage.domain.repository.DayAssignmentRepository
import com.mediasage.domain.repository.FigureRepository
import com.mediasage.domain.repository.HeadlineRepository
import com.mediasage.domain.repository.UserPreferencesRepository
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

class BriefingViewModel(
    private val dayAssignmentRepository: DayAssignmentRepository,
    private val dailyReflectionRepository: DailyReflectionRepository,
    private val figureRepository: FigureRepository,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val headlineRepository: HeadlineRepository,
) : ViewModel() {

    private val _state = MutableStateFlow<BriefingContract.UiState>(
        BriefingContract.UiState.Loading(todayLabel())
    )
    val state: StateFlow<BriefingContract.UiState> = _state.asStateFlow()

    private val _sideEffects = Channel<BriefingContract.SideEffect>(Channel.BUFFERED)
    val sideEffects = _sideEffects.receiveAsFlow()

    init {
        loadCard()
    }

    fun onIntent(intent: BriefingContract.Intent) {
        when (intent) {
            is BriefingContract.Intent.Retry -> loadCard()
        }
    }

    private fun loadCard() {
        viewModelScope.launch {
            combine(
                dayAssignmentRepository.observeAssignments(),
                figureRepository.observeAllFigures(),
                userPreferencesRepository.observeLens(),
            ) { assignments, figures, lens -> Triple(assignments, figures, lens) }
                .distinctUntilChanged()
                .collectLatest { (assignments, figures, _) ->
                    val todayOrdinal = todayDayOfWeekOrdinal()
                    val figureId = assignments[todayOrdinal] ?: figures.firstOrNull()?.id
                    if (figureId == null) {
                        updateCard(BriefingContract.CardState.Hidden)
                        emitLoadingSuccess()
                        return@collectLatest
                    }
                    fetchAndUpdateCard(figureId)
                }
        }
    }

    private suspend fun fetchAndUpdateCard(figureId: Long) {
        val figure = figureRepository.getFigureById(figureId) ?: return
        val tone = currentTone()
        val lens = userPreferencesRepository.observeLens().first()
        val headlines = if (lens == LensFilter.NEWS) {
            headlineRepository.observeHeadlines().first().map { it.title }
        } else {
            emptyList()
        }
        val themeLabel = lens.name.takeIf { lens != LensFilter.NEWS }
        updateCard(
            BriefingContract.CardState.LoadingWithFigure(
                figureId = figureId,
                figureName = figure.name,
                figureImageUrl = figure.portraitUrl,
                theme = themeLabel
            )
        )
        runCatching {
            dailyReflectionRepository.getOrFetch(
                figureId = figure.serverId.takeIf { it > 0 } ?: figureId,
                figureName = figure.name,
                headlines = headlines,
                tone = tone,
                theme = themeLabel
            )
        }.onSuccess { reflection ->
            updateCard(
                BriefingContract.CardState.Ready(
                    figureId = figureId,
                    figureName = figure.name,
                    figureImageUrl = figure.portraitUrl,
                    scriptureReference = reflection.scriptureReference,
                    scriptureText = reflection.scriptureText,
                    reflection = reflection.reflection,
                    sources = reflection.sources,
                    tone = reflection.tone,
                    theme = themeLabel
                )
            )
        }.onFailure { e ->
            updateCard(BriefingContract.CardState.Hidden)
            _sideEffects.send(BriefingContract.SideEffect.ShowError(e.message ?: "Failed to load briefing"))
        }
    }

    private fun updateCard(card: BriefingContract.CardState) {
        val label = todayLabel()
        val current = _state.value
        _state.value = when (current) {
            is BriefingContract.UiState.Loading -> BriefingContract.UiState.Success(label, card)
            is BriefingContract.UiState.Success -> current.copy(card = card)
            is BriefingContract.UiState.Error -> BriefingContract.UiState.Success(label, card)
        }
    }

    private fun emitLoadingSuccess() {
        _state.value = BriefingContract.UiState.Success(
            todayLabel = todayLabel(),
            card = BriefingContract.CardState.Hidden
        )
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
        return "$day, $month ${date.day}, ${date.year}"
    }

    private fun todayDayOfWeekOrdinal(): Int =
        Instant.fromEpochMilliseconds(epochMillis())
            .toLocalDateTime(TimeZone.currentSystemDefault()).date.dayOfWeek.ordinal
}
