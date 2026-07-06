package com.mediasage.feature.you

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mediasage.data.repository.epochMillis
import com.mediasage.domain.model.DailyReflection
import com.mediasage.domain.model.DayAssignment
import com.mediasage.domain.model.Encouragement
import com.mediasage.domain.model.Figure
import com.mediasage.domain.repository.DailyReflectionRepository
import com.mediasage.domain.repository.DayAssignmentRepository
import com.mediasage.domain.repository.EncouragementRepository
import com.mediasage.domain.repository.FigureRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
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

class HistoryViewModel(
    private val initialEpochDay: Long,
    private val reflectionRepository: DailyReflectionRepository,
    private val encouragementRepository: EncouragementRepository,
    private val figureRepository: FigureRepository,
    private val dayAssignmentRepository: DayAssignmentRepository,
) : ViewModel() {

    private val _mode = MutableStateFlow(HistoryContract.CalendarMode.WEEK)
    private val _selectedTab = MutableStateFlow(HistoryContract.DayTab.BRIEFING)
    private val _selectedDay = MutableStateFlow<Long?>(null)
    private val _dayDetail = MutableStateFlow<HistoryContract.DayDetail?>(null)
    private val _calendarAnchor = MutableStateFlow(
        if (initialEpochDay > 0L) LocalDate.fromEpochDays(initialEpochDay.toInt()) else todayLocalDate()
    )
    private var detailJob: Job? = null

    private val _state = MutableStateFlow<HistoryContract.UiState>(HistoryContract.UiState.Loading)
    val state: StateFlow<HistoryContract.UiState> = _state.asStateFlow()

    init {
        val today = todayLocalDate()
        val yearStart = LocalDate(today.year, 1, 1).toEpochDays().toLong()
        val yearEnd = LocalDate(today.year, 12, 31).toEpochDays().toLong()
        combine(
            combine(_mode, _selectedTab, _calendarAnchor) { m, t, a -> Triple(m, t, a) },
            combine(_selectedDay, _dayDetail) { d, detail -> d to detail },
            combine(figureRepository.observeAllFigures(), dayAssignmentRepository.observeAssignments()) { f, a -> f to a },
            encouragementRepository.observeActiveEpochDays(),
            reflectionRepository.observeByEpochDayRange(yearStart, yearEnd),
        ) { modeTabAnchor, selectedPair, figuresAssign, activeEncDays, briefingDays ->
            val (mode, selectedTab, calendarAnchor) = modeTabAnchor
            val (selectedDay, dayDetail) = selectedPair
            val (figures, assignments) = figuresAssign
            val briefingByDay = briefingDays.associate { it.epochDay to it.figureId }
            val figuresById = figures.associateBy { it.id }
            val activeDays = activeEncDays + briefingDays.map { it.epochDay }
            val figure = selectedDay?.let { briefingByDay[it]?.let { fid -> figuresById[fid] } }
            HistoryContract.UiState.Ready(
                mode = mode,
                selectedTab = selectedTab,
                calendarDays = buildCalendarDays(mode, todayLocalDate(), calendarAnchor, activeDays, briefingByDay, figuresById, assignments),
                selectedEpochDay = selectedDay,
                dayDetail = if (selectedDay != null) {
                    dayDetail?.copy(figureName = figure?.name, figureImageUrl = figure?.portraitUrl)
                } else null,
            )
        }.onEach { _state.value = it }.launchIn(viewModelScope)
        if (initialEpochDay > 0L) selectDay(initialEpochDay)
    }

    fun onIntent(intent: HistoryContract.Intent) {
        when (intent) {
            is HistoryContract.Intent.SelectMode -> _mode.value = intent.mode
            is HistoryContract.Intent.SelectTab -> _selectedTab.value = intent.tab
            is HistoryContract.Intent.SelectDay -> selectDay(intent.epochDay)
            is HistoryContract.Intent.ClearSelection -> {
                _selectedDay.value = null
                _dayDetail.value = null
                detailJob?.cancel()
            }
            is HistoryContract.Intent.ToggleBookmark -> viewModelScope.launch {
                encouragementRepository.toggleBookmark(intent.articleUrl)
            }
        }
    }

    private fun selectDay(epochDay: Long) {
        _selectedDay.value = epochDay
        _calendarAnchor.value = LocalDate.fromEpochDays(epochDay.toInt())
        _selectedTab.value = HistoryContract.DayTab.BRIEFING
        _dayDetail.value = null
        detailJob?.cancel()
        detailJob = viewModelScope.launch {
            val reflection = reflectionRepository.getForDay(epochDay)
            encouragementRepository.observeByEpochDay(epochDay).collect { encouragements ->
                _dayDetail.value = HistoryContract.DayDetail(
                    epochDay = epochDay,
                    reflection = reflection?.toSummary(),
                    encouragements = encouragements.map { it.toItem() },
                )
            }
        }
    }

    private fun buildCalendarDays(
        mode: HistoryContract.CalendarMode,
        today: LocalDate,
        anchor: LocalDate,
        activeDays: Set<Long>,
        briefingByDay: Map<Long, Long>,
        figuresById: Map<Long, Figure>,
        assignments: Map<Int, DayAssignment>,
    ): List<HistoryContract.CalendarDay> = when (mode) {
        HistoryContract.CalendarMode.WEEK -> buildWeekDays(today, anchor, activeDays, briefingByDay, figuresById, assignments)
        HistoryContract.CalendarMode.MONTH -> buildMonthDays(today, anchor, activeDays, briefingByDay, figuresById, assignments)
        HistoryContract.CalendarMode.YEAR -> buildYearTiles(today, activeDays)
    }

    private fun buildWeekDays(
        today: LocalDate,
        anchor: LocalDate,
        activeDays: Set<Long>,
        briefingByDay: Map<Long, Long>,
        figuresById: Map<Long, Figure>,
        assignments: Map<Int, DayAssignment>,
    ): List<HistoryContract.CalendarDay> {
        val startOfWeek = anchor.minus(anchor.dayOfWeek.ordinal, DateTimeUnit.DAY)
        val todayEpochDay = today.toEpochDays()
        return (0..6).map { i ->
            val date = startOfWeek.plus(i, DateTimeUnit.DAY)
            val epochDay = date.toEpochDays()
            val isFuture = epochDay > todayEpochDay
            val figure = briefingByDay[epochDay]?.let { figuresById[it] }
                ?: if (isFuture && epochDay <= todayEpochDay + 7) assignments[date.dayOfWeek.ordinal]?.let { figuresById[it.figureId] } else null
            HistoryContract.CalendarDay(
                epochDay = epochDay,
                label = date.dayOfWeek.name.take(3),
                isToday = date == today,
                hasData = epochDay in activeDays,
                isFuture = isFuture,
                figurePortraitUrl = figure?.portraitUrl,
                figureName = figure?.name,
            )
        }
    }

    private fun buildMonthDays(
        today: LocalDate,
        anchor: LocalDate,
        activeDays: Set<Long>,
        briefingByDay: Map<Long, Long>,
        figuresById: Map<Long, Figure>,
        assignments: Map<Int, DayAssignment>,
    ): List<HistoryContract.CalendarDay> {
        val firstOfMonth = LocalDate(anchor.year, anchor.month, 1)
        val daysInMonth = firstOfMonth.plus(1, DateTimeUnit.MONTH).minus(1, DateTimeUnit.DAY).day
        val todayEpochDay = today.toEpochDays()
        return (0 until daysInMonth).map { d ->
            val date = firstOfMonth.plus(d, DateTimeUnit.DAY)
            val epochDay = date.toEpochDays()
            val isFuture = epochDay > todayEpochDay
            val figure = briefingByDay[epochDay]?.let { figuresById[it] }
                ?: if (isFuture && epochDay <= todayEpochDay + 7) assignments[date.dayOfWeek.ordinal]?.let { figuresById[it.figureId] } else null
            HistoryContract.CalendarDay(
                epochDay = epochDay,
                label = "${date.day}",
                isToday = date == today,
                hasData = epochDay in activeDays,
                isFuture = isFuture,
                figurePortraitUrl = figure?.portraitUrl,
                figureName = figure?.name,
            )
        }
    }

    private fun buildYearTiles(today: LocalDate, activeDays: Set<Long>): List<HistoryContract.CalendarDay> {
        return (1..12).map { month ->
            val firstOfMonth = LocalDate(today.year, month, 1)
            val startEpochDay = firstOfMonth.toEpochDays()
            val endEpochDay = firstOfMonth.plus(1, DateTimeUnit.MONTH).toEpochDays()
            HistoryContract.CalendarDay(
                epochDay = startEpochDay,
                label = firstOfMonth.month.name.take(3),
                isToday = firstOfMonth.month == today.month,
                hasData = activeDays.any { it >= startEpochDay && it < endEpochDay },
            )
        }
    }
}

private fun todayLocalDate(): LocalDate =
    Instant.fromEpochMilliseconds(epochMillis()).toLocalDateTime(TimeZone.currentSystemDefault()).date

private fun DailyReflection.toSummary() = HistoryContract.ReflectionSummary(
    scriptureReference = scriptureReference,
    scriptureText = scriptureText,
    insight = insight,
    implication = implication,
    inspiration = inspiration,
    sources = sources,
)

private fun Encouragement.toItem() = HistoryContract.EncouragementItem(
    headlineTitle = headlineTitle,
    quoteText = quoteText,
    figureName = figureName,
    figureRole = figureRole,
    figureImageUrl = figureImageUrl,
    articleUrl = articleUrl ?: "",
    isBookmarked = bookmarked,
)
