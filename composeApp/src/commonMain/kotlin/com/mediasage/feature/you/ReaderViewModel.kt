package com.mediasage.feature.you

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mediasage.data.repository.epochMillis
import com.mediasage.domain.model.DailyReflection
import com.mediasage.domain.model.DayAssignment
import com.mediasage.domain.model.Encouragement
import com.mediasage.domain.model.LensFilter
import com.mediasage.domain.repository.DailyReflectionRepository
import com.mediasage.domain.repository.DayAssignmentRepository
import com.mediasage.domain.repository.EncouragementRepository
import com.mediasage.domain.repository.FigureRepository
import com.mediasage.domain.repository.QuoteRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

@Suppress("TooManyFunctions")
class ReaderViewModel(
    private val figureRepository: FigureRepository,
    private val dayAssignmentRepository: DayAssignmentRepository,
    private val quoteRepository: QuoteRepository,
    private val dailyReflectionRepository: DailyReflectionRepository,
    private val encouragementRepository: EncouragementRepository,
) : ViewModel() {

    private val todayDate = Instant.fromEpochMilliseconds(epochMillis())
        .toLocalDateTime(TimeZone.currentSystemDefault()).date
    private val todayEpochDay = todayDate.toEpochDays().toLong()

    private val _visibleMonth = MutableStateFlow(
        LocalDate(todayDate.year, todayDate.monthNumber, 1)
    )
    private val _state = MutableStateFlow<ReaderContract.UiState>(ReaderContract.UiState.Ready())
    val state: StateFlow<ReaderContract.UiState> = _state.asStateFlow()

    private var dayDetailJob: Job? = null

    init {
        _visibleMonth.flatMapLatest { monthDate ->
            val start = monthDate.toEpochDays().toLong()
            val end = monthDate.plus(1, DateTimeUnit.MONTH).toEpochDays().toLong() - 1
            val overrideStart = minOf(start, todayEpochDay - 7)
            val overrideEnd = maxOf(end, todayEpochDay + 7)
            combine(
                figureRepository.observeAllFigures(),
                dayAssignmentRepository.observeAssignments(),
                quoteRepository.observeAllQuotes(),
                dailyReflectionRepository.observeByEpochDayRange(start, end),
                dayAssignmentRepository.observeOverridesByEpochDayRange(overrideStart, overrideEnd),
            ) { figures, assignments, allQuotes, briefingDays, overridesByDay ->
                buildState(figures, assignments, allQuotes, briefingDays, overridesByDay, start, end)
            }
        }.onEach { _state.value = it }.launchIn(viewModelScope)
    }

    fun onIntent(intent: ReaderContract.Intent) {
        val current = _state.value as? ReaderContract.UiState.Ready ?: return
        when (intent) {
            is ReaderContract.Intent.DaySlotTapped ->
                _state.value = current.copy(
                    activeSheet = ReaderContract.ActiveSheet.WeekSlotPicker(
                        current.weekSlots[intent.index].dayOfWeek.ordinal
                    )
                )

            is ReaderContract.Intent.PickerDismissed -> {
                dayDetailJob?.cancel()
                _state.value = current.copy(activeSheet = null)
            }

            is ReaderContract.Intent.FigureAssigned -> viewModelScope.launch {
                dayAssignmentRepository.assign(intent.dayOfWeek, intent.figureId, intent.lens)
                _state.value = current.copy(activeSheet = null)
            }

            is ReaderContract.Intent.AssignmentCleared -> viewModelScope.launch {
                dayAssignmentRepository.clear(intent.dayOfWeek)
                _state.value = current.copy(activeSheet = null)
            }

            is ReaderContract.Intent.SelectFutureDay ->
                _state.value = current.copy(
                    activeSheet = ReaderContract.ActiveSheet.FutureDayPicker(intent.epochDay)
                )

            is ReaderContract.Intent.AssignOverride -> viewModelScope.launch {
                dayAssignmentRepository.setOverride(intent.epochDay, intent.figureId)
                _state.value = current.copy(activeSheet = null)
            }

            is ReaderContract.Intent.ClearOverride -> viewModelScope.launch {
                dayAssignmentRepository.clearOverride(intent.epochDay)
                _state.value = current.copy(activeSheet = null)
            }

            is ReaderContract.Intent.ToggleCalendarExpanded ->
                _state.value = current.copy(isCalendarExpanded = !current.isCalendarExpanded)

            is ReaderContract.Intent.MonthPageChanged -> {
                val newDate = LocalDate(intent.year, intent.month, 1)
                if (_visibleMonth.value != newDate) _visibleMonth.value = newDate
            }

            is ReaderContract.Intent.HistoryDayTapped -> {
                val calDay = current.calendarDays.find { it.epochDay == intent.epochDay }
                val stub = ReaderContract.DayDetail(
                    epochDay = intent.epochDay,
                    reflection = null,
                    articles = emptyList(),
                    figureName = calDay?.figureName,
                    figureImageUrl = calDay?.figurePortraitUrl,
                )
                _state.value = current.copy(activeSheet = ReaderContract.ActiveSheet.HistoryDetail(stub))
                loadDayDetail(intent.epochDay, calDay?.figureName, calDay?.figurePortraitUrl)
            }
        }
    }

    private fun loadDayDetail(epochDay: Long, figureName: String?, figureImageUrl: String?) {
        dayDetailJob?.cancel()
        dayDetailJob = viewModelScope.launch {
            val reflection = dailyReflectionRepository.getForDay(epochDay)
            encouragementRepository.observeByEpochDay(epochDay).collect { encouragements ->
                val cur = _state.value as? ReaderContract.UiState.Ready ?: return@collect
                val detail = ReaderContract.DayDetail(
                    epochDay = epochDay,
                    reflection = reflection?.toReaderSummary(),
                    articles = encouragements.map { it.toArticleItem() },
                    figureName = figureName,
                    figureImageUrl = figureImageUrl,
                )
                _state.value = cur.copy(activeSheet = ReaderContract.ActiveSheet.HistoryDetail(detail))
            }
        }
    }

    private fun buildState(
        figures: List<com.mediasage.domain.model.Figure>,
        assignments: Map<Int, DayAssignment>,
        allQuotes: List<com.mediasage.domain.model.Quote>,
        briefingDays: List<com.mediasage.domain.model.BriefingDay>,
        overridesByDay: Map<Long, Long>,
        monthStartEpoch: Long,
        monthEndEpoch: Long,
    ): ReaderContract.UiState.Ready {
        val current = _state.value as? ReaderContract.UiState.Ready ?: ReaderContract.UiState.Ready()
        val figuresById = figures.associateBy { it.id }
        val latestQuote = allQuotes.maxByOrNull { it.id }
        val quoteFigure = latestQuote?.let { figuresById[it.figureId] }
        val briefingByDay = briefingDays.associate { it.epochDay to it.figureId }
        val daysInMonth = (monthEndEpoch - monthStartEpoch + 1).toInt()
        return current.copy(
            weekSlots = buildWeekSlots(assignments, overridesByDay, figuresById),
            pickerFigures = figures,
            quoteCard = buildQuoteCard(latestQuote, quoteFigure),
            calendarDays = buildCalendarDays(monthStartEpoch, daysInMonth, briefingByDay, assignments, overridesByDay, figuresById),
        )
    }

    private fun buildWeekSlots(
        assignments: Map<Int, DayAssignment>,
        overridesByDay: Map<Long, Long>,
        figuresById: Map<Long, com.mediasage.domain.model.Figure>,
    ): List<ReaderContract.DaySlot> {
        val today = Instant.fromEpochMilliseconds(epochMillis())
            .toLocalDateTime(TimeZone.currentSystemDefault()).date
        val startOfWeek = today.minus(today.dayOfWeek.ordinal, DateTimeUnit.DAY)
        return (0..6).map { i ->
            val date = startOfWeek.plus(i, DateTimeUnit.DAY)
            val epochDay = date.toEpochDays().toLong()
            val overrideFigureId = if (epochDay > todayEpochDay) overridesByDay[epochDay] else null
            val assignment = assignments[date.dayOfWeek.ordinal]
            val figureId = overrideFigureId ?: assignment?.figureId
            val figure = figureId?.let { figuresById[it] }
            ReaderContract.DaySlot(
                dayOfWeek = date.dayOfWeek,
                epochDay = epochDay,
                isToday = date == today,
                assignedFigureName = figure?.name,
                assignedFigureImageUrl = figure?.portraitUrl,
                assignedLens = if (overrideFigureId != null) null else assignment?.lens,
            )
        }
    }

    private fun buildCalendarDays(
        monthStartEpoch: Long,
        daysInMonth: Int,
        briefingByDay: Map<Long, Long>,
        assignments: Map<Int, DayAssignment>,
        overridesByDay: Map<Long, Long>,
        figuresById: Map<Long, com.mediasage.domain.model.Figure>,
    ): List<ReaderContract.CalendarDay> {
        return (0 until daysInMonth).map { d ->
            val epochDay = monthStartEpoch + d
            val date = LocalDate.fromEpochDays(epochDay.toInt())
            val isFuture = epochDay > todayEpochDay
            val overrideFigureId = overridesByDay[epochDay]
            val figureId = if (isFuture) overrideFigureId
                           else briefingByDay[epochDay]
            val figure = figureId?.let { figuresById[it] }
            ReaderContract.CalendarDay(
                epochDay = epochDay,
                dateNumber = date.dayOfMonth,
                isToday = epochDay == todayEpochDay,
                isFuture = isFuture,
                hasData = figureId != null,
                figurePortraitUrl = figure?.portraitUrl,
                figureName = figure?.name,
                overrideFigureId = overrideFigureId,
            )
        }
    }

    private fun buildQuoteCard(
        latestQuote: com.mediasage.domain.model.Quote?,
        quoteFigure: com.mediasage.domain.model.Figure?,
    ): ReaderContract.QuoteCard? {
        if (latestQuote == null || quoteFigure == null) return null
        return ReaderContract.QuoteCard(
            quoteText = latestQuote.text,
            figureName = quoteFigure.name,
            figureRole = quoteFigure.role,
            figureImageUrl = quoteFigure.portraitUrl,
            figureId = quoteFigure.id,
        )
    }
}

private fun DailyReflection.toReaderSummary() = ReaderContract.ReflectionSummary(
    scriptureReference = scriptureReference,
    scriptureText = scriptureText,
    insight = insight,
    implication = implication,
    inspiration = inspiration,
    sources = sources,
)

private fun Encouragement.toArticleItem() = ReaderContract.ArticleItem(
    headlineTitle = headlineTitle,
    quoteText = quoteText,
    figureName = figureName,
    figureRole = figureRole,
    figureImageUrl = figureImageUrl,
    articleUrl = articleUrl ?: "",
)
