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

    private val _state = MutableStateFlow<HistoryContract.UiState>(HistoryContract.UiState.Loading)
    val state: StateFlow<HistoryContract.UiState> = _state.asStateFlow()

    private val initialAnchor =
        if (initialEpochDay > 0L) LocalDate.fromEpochDays(initialEpochDay.toInt()) else todayLocalDate()

    private var lastFiguresById: Map<Long, Figure> = emptyMap()
    private var lastAssignments: Map<Int, DayAssignment> = emptyMap()
    private var lastActiveDays: Set<Long> = emptySet()
    private var lastBriefingByDay: Map<Long, Long> = emptyMap()
    private var initialDayHandled = false
    private var detailJob: Job? = null

    init {
        val today = todayLocalDate()
        val yearStart = LocalDate(today.year, 1, 1).toEpochDays().toLong()
        val yearEnd = LocalDate(today.year, 12, 31).toEpochDays().toLong()
        combine(
            combine(figureRepository.observeAllFigures(), dayAssignmentRepository.observeAssignments()) { f, a -> f to a },
            encouragementRepository.observeActiveEpochDays(),
            reflectionRepository.observeByEpochDayRange(yearStart, yearEnd),
        ) { (figures, assignments), activeEncDays, briefingDays ->
            lastFiguresById = figures.associateBy { it.id }
            lastAssignments = assignments
            lastBriefingByDay = briefingDays.associate { it.epochDay to it.figureId }
            lastActiveDays = activeEncDays + briefingDays.map { it.epochDay }
            buildReady(today)
        }.onEach { ready ->
            _state.value = ready
            if (!initialDayHandled && initialEpochDay > 0L) {
                initialDayHandled = true
                selectDay(initialEpochDay)
            }
        }.launchIn(viewModelScope)
    }

    fun onIntent(intent: HistoryContract.Intent) {
        when (intent) {
            is HistoryContract.Intent.SelectMode -> updateReady { current ->
                val today = todayLocalDate()
                current.copy(
                    mode = intent.mode,
                    calendarDays = buildCalendarDays(
                        intent.mode, today, current.calendarAnchor,
                        lastActiveDays, lastBriefingByDay, lastFiguresById, lastAssignments,
                    ),
                )
            }
            is HistoryContract.Intent.SelectTab -> updateReady { it.copy(selectedTab = intent.tab) }
            is HistoryContract.Intent.SelectDay -> selectDay(intent.epochDay)
            is HistoryContract.Intent.ClearSelection -> updateReady { current ->
                detailJob?.cancel()
                current.copy(selectedEpochDay = null, dayDetail = null)
            }
            is HistoryContract.Intent.ToggleBookmark -> viewModelScope.launch {
                encouragementRepository.toggleBookmark(intent.articleUrl)
            }
        }
    }

    private fun buildReady(today: LocalDate): HistoryContract.UiState.Ready {
        val current = _state.value as? HistoryContract.UiState.Ready
        val mode = current?.mode ?: HistoryContract.CalendarMode.WEEK
        val calendarAnchor = current?.calendarAnchor ?: initialAnchor
        val selectedEpochDay = current?.selectedEpochDay
        val dayDetail = current?.dayDetail
        val figure = selectedEpochDay?.let { lastBriefingByDay[it]?.let { fid -> lastFiguresById[fid] } }
        return HistoryContract.UiState.Ready(
            mode = mode,
            selectedTab = current?.selectedTab ?: HistoryContract.DayTab.BRIEFING,
            calendarAnchor = calendarAnchor,
            calendarDays = buildCalendarDays(
                mode, today, calendarAnchor, lastActiveDays, lastBriefingByDay, lastFiguresById, lastAssignments,
            ),
            selectedEpochDay = selectedEpochDay,
            dayDetail = if (selectedEpochDay != null) {
                dayDetail?.copy(figureName = figure?.name, figureImageUrl = figure?.portraitUrl)
            } else null,
        )
    }

    private inline fun updateReady(block: (HistoryContract.UiState.Ready) -> HistoryContract.UiState.Ready) {
        val current = (_state.value as? HistoryContract.UiState.Ready) ?: return
        _state.value = block(current)
    }

    private fun selectDay(epochDay: Long) {
        detailJob?.cancel()
        val anchor = LocalDate.fromEpochDays(epochDay.toInt())
        updateReady { current ->
            current.copy(
                selectedEpochDay = epochDay,
                calendarAnchor = anchor,
                selectedTab = HistoryContract.DayTab.BRIEFING,
                dayDetail = null,
            )
        }
        detailJob = viewModelScope.launch {
            val reflection = reflectionRepository.getForDay(epochDay)
            encouragementRepository.observeByEpochDay(epochDay).collect { encouragements ->
                updateReady { current ->
                    current.copy(
                        dayDetail = HistoryContract.DayDetail(
                            epochDay = epochDay,
                            reflection = reflection?.toSummary(),
                            encouragements = encouragements.map { it.toItem() },
                        ),
                    )
                }
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
        HistoryContract.CalendarMode.WEEK ->
            buildWeekDays(today, anchor, activeDays, briefingByDay, figuresById, assignments)
        HistoryContract.CalendarMode.MONTH ->
            buildMonthDays(today, anchor, activeDays, briefingByDay, figuresById, assignments)
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
                ?: if (isFuture && epochDay <= todayEpochDay + 7) {
                    assignments[date.dayOfWeek.ordinal]?.let { figuresById[it.figureId] }
                } else null
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
                ?: if (isFuture && epochDay <= todayEpochDay + 7) {
                    assignments[date.dayOfWeek.ordinal]?.let { figuresById[it.figureId] }
                } else null
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
