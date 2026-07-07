package com.mediasage.feature.you

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mediasage.data.repository.epochMillis
import com.mediasage.domain.model.DayAssignment
import com.mediasage.domain.model.LensFilter
import com.mediasage.domain.repository.DailyReflectionRepository
import com.mediasage.domain.repository.DayAssignmentRepository
import com.mediasage.domain.repository.FigureRepository
import com.mediasage.domain.repository.QuoteRepository
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

class ReaderViewModel(
    private val figureRepository: FigureRepository,
    private val dayAssignmentRepository: DayAssignmentRepository,
    private val quoteRepository: QuoteRepository,
    private val dailyReflectionRepository: DailyReflectionRepository,
) : ViewModel() {

    private val todayDate = Instant.fromEpochMilliseconds(epochMillis())
        .toLocalDateTime(TimeZone.currentSystemDefault()).date
    private val todayEpochDay = todayDate.toEpochDays().toLong()
    private val monthFirstDay = LocalDate(todayDate.year, todayDate.monthNumber, 1)
    private val monthStartEpoch = monthFirstDay.toEpochDays().toLong()
    private val monthEndEpoch = monthFirstDay.plus(1, DateTimeUnit.MONTH).toEpochDays().toLong() - 1
    private val daysInMonth = (monthEndEpoch - monthStartEpoch + 1).toInt()

    private val _state = MutableStateFlow<ReaderContract.UiState>(ReaderContract.UiState.Ready())
    val state: StateFlow<ReaderContract.UiState> = _state.asStateFlow()

    init {
        combine(
            figureRepository.observeAllFigures(),
            dayAssignmentRepository.observeAssignments(),
            quoteRepository.observeAllQuotes(),
            dailyReflectionRepository.observeByEpochDayRange(monthStartEpoch, monthEndEpoch),
            dayAssignmentRepository.observeOverridesByEpochDayRange(monthStartEpoch, monthEndEpoch),
        ) { figures, assignments, allQuotes, briefingDays, overridesByDay ->
            val current = _state.value as? ReaderContract.UiState.Ready ?: ReaderContract.UiState.Ready()
            val figuresById = figures.associateBy { it.id }
            val latestQuote = allQuotes.maxByOrNull { it.id }
            val quoteFigure = latestQuote?.let { figuresById[it.figureId] }
            val briefingByDay = briefingDays.associate { it.epochDay to it.figureId }
            current.copy(
                weekSlots = buildWeekSlots(assignments, overridesByDay, figuresById),
                pickerFigures = figures,
                quoteCard = buildQuoteCard(latestQuote, quoteFigure),
                calendarDays = buildCalendarDays(briefingByDay, overridesByDay, figuresById),
            )
        }.onEach { _state.value = it }.launchIn(viewModelScope)
    }

    fun onIntent(intent: ReaderContract.Intent) {
        val current = _state.value as? ReaderContract.UiState.Ready ?: return
        when (intent) {
            is ReaderContract.Intent.DaySlotTapped ->
                _state.value = current.copy(pickerOpenForDay = current.weekSlots[intent.index].dayOfWeek.ordinal)

            is ReaderContract.Intent.PickerDismissed ->
                _state.value = current.copy(pickerOpenForDay = null, pickerOpenForEpochDay = null)

            is ReaderContract.Intent.FigureAssigned -> viewModelScope.launch {
                dayAssignmentRepository.assign(intent.dayOfWeek, intent.figureId, intent.lens)
                _state.value = current.copy(pickerOpenForDay = null)
            }

            is ReaderContract.Intent.AssignmentCleared -> viewModelScope.launch {
                dayAssignmentRepository.clear(intent.dayOfWeek)
                _state.value = current.copy(pickerOpenForDay = null)
            }

            is ReaderContract.Intent.SelectFutureDay ->
                _state.value = current.copy(pickerOpenForEpochDay = intent.epochDay)

            is ReaderContract.Intent.AssignOverride -> viewModelScope.launch {
                dayAssignmentRepository.setOverride(intent.epochDay, intent.figureId)
                _state.value = current.copy(pickerOpenForEpochDay = null)
            }

            is ReaderContract.Intent.ClearOverride -> viewModelScope.launch {
                dayAssignmentRepository.clearOverride(intent.epochDay)
                _state.value = current.copy(pickerOpenForEpochDay = null)
            }
        }
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
                isToday = date == today,
                assignedFigureName = figure?.name,
                assignedFigureImageUrl = figure?.portraitUrl,
                assignedLens = if (overrideFigureId != null) null else assignment?.lens,
            )
        }
    }

    private fun buildCalendarDays(
        briefingByDay: Map<Long, Long>,
        overridesByDay: Map<Long, Long>,
        figuresById: Map<Long, com.mediasage.domain.model.Figure>,
    ): List<ReaderContract.CalendarDay> {
        return (0 until daysInMonth).map { d ->
            val epochDay = monthStartEpoch + d
            val date = LocalDate.fromEpochDays(epochDay.toInt())
            val isFuture = epochDay > todayEpochDay
            val overrideFigureId = overridesByDay[epochDay]
            val figureId = if (isFuture) overrideFigureId else briefingByDay[epochDay]
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
