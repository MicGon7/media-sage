package com.mediasage.feature.briefing

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mediasage.data.repository.epochMillis
import com.mediasage.domain.model.DayAssignment
import com.mediasage.domain.model.LensFilter
import com.mediasage.domain.repository.DailyReflectionRepository
import com.mediasage.domain.repository.DayAssignmentRepository
import com.mediasage.domain.repository.FigureRepository
import com.mediasage.domain.repository.HeadlineRepository
import kotlinx.coroutines.CancellationException
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
            ) { assignments, figures -> Pair(assignments, figures) }
                .distinctUntilChanged()
                .collectLatest { (assignments, figures) ->
                    val todayOrdinal = todayDayOfWeekOrdinal()
                    val figureId = dayAssignmentRepository.resolveReporter(todayEpochDay(), todayOrdinal)
                        ?: figures.firstOrNull()?.id
                    if (figureId == null) {
                        updateCard(BriefingContract.CardState.Hidden)
                        emitLoadingSuccess()
                        return@collectLatest
                    }
                    fetchAndUpdateCard(figureId, resolveLens(figureId, todayOrdinal, assignments))
                }
        }
    }

    /**
     * Once today is locked to a figure, a newer weekday reassignment may no longer describe that
     * figure's lens — fall back to the theme already cached on today's reflection in that case.
     */
    private suspend fun resolveLens(
        figureId: Long,
        dayOfWeek: Int,
        assignments: Map<Int, DayAssignment>,
    ): LensFilter? {
        assignments[dayOfWeek]?.takeIf { it.figureId == figureId }?.let { return it.lens }
        val cachedTheme = dailyReflectionRepository.getForDay(todayEpochDay(), currentTone())?.theme
        return cachedTheme?.let { name -> LensFilter.entries.firstOrNull { it.name == name } }
    }

    private suspend fun fetchAndUpdateCard(figureId: Long, lens: LensFilter?) {
        val figure = figureRepository.getFigureById(figureId) ?: return
        val tone = currentTone()
        val effectiveLens = lens ?: LensFilter.NEWS
        val headlines = if (effectiveLens == LensFilter.NEWS) {
            headlineRepository.observeHeadlines().first().map { it.title }
        } else {
            emptyList()
        }
        val themeLabel = effectiveLens.name.takeIf { effectiveLens != LensFilter.NEWS }
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
                    insight = reflection.insight,
                    implication = reflection.implication,
                    inspiration = reflection.inspiration,
                    sources = reflection.sources,
                    tone = reflection.tone,
                    theme = themeLabel
                )
            )
        }.onFailure { e ->
            if (e is CancellationException) throw e
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

    private fun todayEpochDay(): Long =
        Instant.fromEpochMilliseconds(epochMillis())
            .toLocalDateTime(TimeZone.currentSystemDefault()).date.toEpochDays().toLong()
}
