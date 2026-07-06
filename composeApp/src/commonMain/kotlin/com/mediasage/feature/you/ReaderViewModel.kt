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

private const val MS_PER_DAY = 86_400_000L

class ReaderViewModel(
    private val figureRepository: FigureRepository,
    private val dayAssignmentRepository: DayAssignmentRepository,
    private val quoteRepository: QuoteRepository,
    private val dailyReflectionRepository: DailyReflectionRepository,
) : ViewModel() {

    private val todayEpochDay = epochMillis() / MS_PER_DAY
    private val todayDate = Instant.fromEpochMilliseconds(epochMillis())
        .toLocalDateTime(TimeZone.currentSystemDefault()).date
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
        ) { figures, assignments, allQuotes, briefingDays ->
            val current = _state.value as? ReaderContract.UiState.Ready ?: ReaderContract.UiState.Ready()
            val figuresById = figures.associateBy { it.id }
            val latestQuote = allQuotes.maxByOrNull { it.id }
            val quoteFigure = latestQuote?.let { figuresById[it.figureId] }
            val briefingByDay = briefingDays.associate { it.epochDay to it.figureId }
            current.copy(
                weekSlots = buildWeekSlots(assignments, figuresById),
                pickerFigures = figures,
                quoteCard = buildQuoteCard(latestQuote, quoteFigure),
                calendarDays = buildCalendarDays(briefingByDay, figuresById),
            )
        }.onEach { _state.value = it }.launchIn(viewModelScope)
    }

    fun onIntent(intent: ReaderContract.Intent) {
        val current = _state.value as? ReaderContract.UiState.Ready ?: return
        when (intent) {
            is ReaderContract.Intent.DaySlotTapped ->
                _state.value = current.copy(pickerOpenForDay = current.weekSlots[intent.index].dayOfWeek.ordinal)

            is ReaderContract.Intent.PickerDismissed ->
                _state.value = current.copy(pickerOpenForDay = null)

            is ReaderContract.Intent.FigureAssigned -> viewModelScope.launch {
                dayAssignmentRepository.assign(intent.dayOfWeek, intent.figureId, intent.lens)
                _state.value = current.copy(pickerOpenForDay = null)
            }

            is ReaderContract.Intent.AssignmentCleared -> viewModelScope.launch {
                dayAssignmentRepository.clear(intent.dayOfWeek)
                _state.value = current.copy(pickerOpenForDay = null)
            }
        }
    }

    private fun buildWeekSlots(
        assignments: Map<Int, DayAssignment>,
        figuresById: Map<Long, com.mediasage.domain.model.Figure>,
    ): List<ReaderContract.DaySlot> {
        val today = Instant.fromEpochMilliseconds(epochMillis())
            .toLocalDateTime(TimeZone.currentSystemDefault()).date
        val startOfWeek = today.minus(today.dayOfWeek.ordinal, DateTimeUnit.DAY)
        return (0..6).map { i ->
            val date = startOfWeek.plus(i, DateTimeUnit.DAY)
            val assignment = assignments[date.dayOfWeek.ordinal]
            val figure = assignment?.figureId?.let { figuresById[it] }
            ReaderContract.DaySlot(
                dayOfWeek = date.dayOfWeek,
                isToday = date == today,
                assignedFigureName = figure?.name,
                assignedFigureImageUrl = figure?.portraitUrl,
                assignedLens = assignment?.lens,
            )
        }
    }

    private fun buildCalendarDays(
        briefingByDay: Map<Long, Long>,
        figuresById: Map<Long, com.mediasage.domain.model.Figure>,
    ): List<ReaderContract.CalendarDay> {
        return (0 until daysInMonth).map { d ->
            val epochDay = monthStartEpoch + d
            val date = LocalDate.fromEpochDays(epochDay.toInt())
            val figureId = briefingByDay[epochDay]
            val figure = figureId?.let { figuresById[it] }
            ReaderContract.CalendarDay(
                epochDay = epochDay,
                dateNumber = date.dayOfMonth,
                isToday = epochDay == todayEpochDay,
                hasData = figureId != null,
                figurePortraitUrl = figure?.portraitUrl,
                figureName = figure?.name,
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
