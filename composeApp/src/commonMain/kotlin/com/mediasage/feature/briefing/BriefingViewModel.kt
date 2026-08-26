package com.mediasage.feature.briefing

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mediasage.data.repository.epochMillis
import com.mediasage.domain.model.DailyReflection
import com.mediasage.domain.model.DayAssignment
import com.mediasage.domain.model.Figure
import com.mediasage.domain.model.LensFilter
import com.mediasage.domain.repository.DailyReflectionRepository
import com.mediasage.domain.repository.DayAssignmentRepository
import com.mediasage.domain.repository.FigureRepository
import com.mediasage.domain.repository.HeadlineRepository
import com.mediasage.domain.repository.UserReflectionNoteRepository
import com.mediasage.feature.headlines.HeadlineCategoryFilter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
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
    private val userReflectionNoteRepository: UserReflectionNoteRepository,
    private val toneScheduler: BriefingToneScheduler = RealBriefingToneScheduler(),
) : ViewModel() {

    private val _state = MutableStateFlow<BriefingContract.UiState>(
        BriefingContract.UiState.Loading(todayLabel())
    )
    val state: StateFlow<BriefingContract.UiState> = _state.asStateFlow()

    private val _sideEffects = Channel<BriefingContract.SideEffect>(Channel.BUFFERED)
    val sideEffects = _sideEffects.receiveAsFlow()

    private var loadJob: Job? = null

    init {
        loadCard()
        awaitToneBoundaryThenReload()
    }

    fun onIntent(intent: BriefingContract.Intent) {
        when (intent) {
            is BriefingContract.Intent.Retry -> loadCard()
            is BriefingContract.Intent.ReflectTapped -> openReflectSheet()
            is BriefingContract.Intent.ReflectDismissed -> updateReflectSheet(null)
            is BriefingContract.Intent.ReflectNoteChanged -> {
                val success = _state.value as? BriefingContract.UiState.Success ?: return
                val noteText = intent.noteText.take(MAX_NOTE_LENGTH)
                updateReflectSheet(success.reflectSheet?.copy(noteText = noteText))
            }
            is BriefingContract.Intent.ReflectNoteSaved -> saveReflectNote()
        }
    }

    private fun updateReflectSheet(sheet: BriefingContract.ReflectSheetState?) {
        val success = _state.value as? BriefingContract.UiState.Success ?: return
        _state.value = success.copy(reflectSheet = sheet)
    }

    private fun openReflectSheet() {
        val success = _state.value as? BriefingContract.UiState.Success ?: return
        val ready = success.card as? BriefingContract.CardState.Ready ?: return
        val challenge = ready.challenge ?: return
        viewModelScope.launch {
            val saved = userReflectionNoteRepository.getNote(reflectionId(ready.tone, ready.theme)).orEmpty()
            updateReflectSheet(BriefingContract.ReflectSheetState(challenge, saved, saved))
        }
    }

    private fun saveReflectNote() {
        val success = _state.value as? BriefingContract.UiState.Success ?: return
        val ready = success.card as? BriefingContract.CardState.Ready ?: return
        val sheet = success.reflectSheet ?: return
        viewModelScope.launch {
            userReflectionNoteRepository.saveNote(reflectionId(ready.tone, ready.theme), sheet.noteText)
            updateReflectSheet(sheet.copy(savedNoteText = sheet.noteText))
        }
    }

    private fun reflectionId(tone: String, theme: String?): String =
        DailyReflection.id(todayEpochDay(), tone, theme)

    /**
     * Reloads once at each tone boundary so a screen left open across the 5pm/midnight transition
     * updates on its own — the boundary crossing is the only wake-up, never a fixed-interval poll.
     */
    private fun awaitToneBoundaryThenReload() {
        viewModelScope.launch {
            while (true) {
                toneScheduler.awaitNextToneBoundary()
                loadCard()
            }
        }
    }

    private fun loadCard() {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            // isResolved is a *live* input here, not a one-time gate — a cold start on a fresh
            // install can flip it true once for a signed-out fallback-defaults seed, then false
            // again moments later while the real signed-in schedule/reflections are pulled down
            // and correct that seeded data. Folding it into the combine (rather than awaiting it
            // once before subscribing) means that correction shows a loading state again instead
            // of silently swapping the fallback figure for the real one after the fact.
            combine(
                dayAssignmentRepository.isResolved,
                dailyReflectionRepository.isResolved,
                dayAssignmentRepository.observeAssignments(),
                figureRepository.observeAllFigures(),
            ) { dayResolved, reflectionResolved, assignments, figures ->
                LoadInputs(dayResolved && reflectionResolved, assignments, figures)
            }
                .distinctUntilChanged()
                .collectLatest { inputs ->
                    if (!inputs.isResolved) {
                        updateCard(BriefingContract.CardState.Loading)
                        return@collectLatest
                    }
                    val todayOrdinal = todayDayOfWeekOrdinal()
                    val figureId = dayAssignmentRepository.resolveReporter(todayEpochDay(), todayOrdinal)
                        ?: inputs.figures.firstOrNull()?.id
                    if (figureId == null) {
                        updateCard(BriefingContract.CardState.Hidden)
                        emitLoadingSuccess()
                        return@collectLatest
                    }
                    fetchAndUpdateCard(figureId, resolveLens(figureId, todayOrdinal, inputs.assignments))
                }
        }
    }

    private data class LoadInputs(
        val isResolved: Boolean,
        val assignments: Map<Int, DayAssignment>,
        val figures: List<Figure>,
    )

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
            headlineRepository.observeHeadlines().first()
                .filter { it.category in BRIEFING_NEWS_CATEGORIES }
                .map { it.title }
        } else {
            emptyList()
        }
        val themeLabel = effectiveLens.name
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
                    theme = themeLabel,
                    challenge = reflection.challenge,
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
        return if (hour < TONE_BOUNDARY_HOUR) "morning" else "evening"
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

private const val MAX_NOTE_LENGTH = 4_000

/**
 * The full set of categories the app surfaces to users, independent of whatever the user
 * currently has selected on the Headlines screen (HeadlineCategoryPreferencesRepository) —
 * that selection is a single-select browsing filter, not a briefing preference.
 */
private val BRIEFING_NEWS_CATEGORIES = HeadlineCategoryFilter.entries.map { it.value }.toSet()
