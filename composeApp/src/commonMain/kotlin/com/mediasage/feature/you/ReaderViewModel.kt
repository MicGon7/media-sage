package com.mediasage.feature.you

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mediasage.data.repository.epochMillis
import com.mediasage.domain.model.DayAssignment
import com.mediasage.domain.model.Figure
import com.mediasage.domain.model.Quote
import com.mediasage.domain.model.ReaderCalendarData
import com.mediasage.domain.repository.DayAssignmentRepository
import com.mediasage.domain.usecase.GetReaderCalendarUseCase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

/**
 * Reference implementation of the Now in Android reactive state-holder pattern for UI state derived
 * from both user selection and live repository streams (see composeApp/CLAUDE.md, "State-holder
 * pattern").
 *
 * Owns only the recurring weekly schedule and today's saved quote — the read-only month-by-month
 * history view lives in [ReaderHistoryViewModel] instead. The open picker sheet is the single user
 * selection, held in [input] and combined with the calendar domain stream
 * ([GetReaderCalendarUseCase]) to derive [ReaderContract.UiState].
 */
class ReaderViewModel(
    private val getReaderCalendar: GetReaderCalendarUseCase,
    private val dayAssignmentRepository: DayAssignmentRepository,
) : ViewModel() {

    private val today = Instant.fromEpochMilliseconds(epochMillis())
        .toLocalDateTime(TimeZone.currentSystemDefault()).date
    private val startOfWeek = today.minus(today.dayOfWeek.ordinal, DateTimeUnit.DAY)
    private val startOfWeekEpochDay = startOfWeek.toEpochDays().toLong()
    private val endOfWeekEpochDay =
        today.plus(DayOfWeek.SUNDAY.ordinal - today.dayOfWeek.ordinal, DateTimeUnit.DAY).toEpochDays().toLong()

    /** The only user selection this screen owns: which day's picker sheet, if any, is open. */
    private val input = MutableStateFlow<ReaderContract.ActiveSheet?>(null)

    private val calendarData: Flow<ReaderCalendarData> = getReaderCalendar(startOfWeekEpochDay, endOfWeekEpochDay)

    val state: StateFlow<ReaderContract.UiState> =
        combine(input, calendarData) { activeSheet, data -> buildReady(activeSheet, data) }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            initialValue = ReaderContract.UiState.Ready(),
        )

    fun onIntent(intent: ReaderContract.Intent) {
        when (intent) {
            is ReaderContract.Intent.DaySlotTapped ->
                input.value = ReaderContract.ActiveSheet.WeekSlotPicker(intent.index)
            is ReaderContract.Intent.PickerDismissed -> input.value = null
            is ReaderContract.Intent.FigureAssigned -> writeThenCloseSheet {
                dayAssignmentRepository.assign(intent.dayOfWeek, intent.figureId, intent.lens)
            }
            is ReaderContract.Intent.AssignmentCleared -> writeThenCloseSheet {
                dayAssignmentRepository.clear(intent.dayOfWeek)
            }
        }
    }

    /** Run a repository write event, then close the open sheet once it completes. */
    private fun writeThenCloseSheet(block: suspend () -> Unit) {
        viewModelScope.launch {
            block()
            input.value = null
        }
    }

    private fun buildReady(
        activeSheet: ReaderContract.ActiveSheet?,
        data: ReaderCalendarData,
    ): ReaderContract.UiState.Ready {
        val figuresById = data.figures.associateBy { it.id }
        val quoteFigure = data.latestQuote?.let { figuresById[it.figureId] }
        return ReaderContract.UiState.Ready(
            weekSlots = buildWeekSlots(figuresById, data.assignmentsByDayOfWeek),
            pickerFigures = data.figures,
            quoteCard = buildQuoteCard(data.latestQuote, quoteFigure),
            activeSheet = activeSheet,
        )
    }

    private fun buildWeekSlots(
        figuresById: Map<Long, Figure>,
        assignments: Map<Int, DayAssignment>,
    ): List<ReaderContract.DaySlot> = (0..6).map { i ->
        val date = startOfWeek.plus(i, DateTimeUnit.DAY)
        val epochDay = date.toEpochDays().toLong()
        val assignment = assignments[date.dayOfWeek.ordinal]
        val figure = assignment?.figureId?.let { figuresById[it] }
        ReaderContract.DaySlot(
            dayOfWeek = date.dayOfWeek,
            epochDay = epochDay,
            isToday = date == today,
            assignedFigureName = figure?.name,
            assignedFigureImageUrl = figure?.portraitUrl,
            assignedLens = assignment?.lens,
        )
    }

    private fun buildQuoteCard(latestQuote: Quote?, quoteFigure: Figure?): ReaderContract.QuoteCard? {
        if (latestQuote == null || quoteFigure == null) return null
        return ReaderContract.QuoteCard(
            quoteText = latestQuote.text,
            figureName = quoteFigure.name,
            figureRole = quoteFigure.role,
            figureImageUrl = quoteFigure.portraitUrl,
            figureId = quoteFigure.id,
        )
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
