package com.mediasage.feature.you

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mediasage.data.repository.epochMillis
import com.mediasage.domain.model.DayAssignment
import com.mediasage.domain.model.Figure
import com.mediasage.domain.model.Quote
import com.mediasage.domain.model.ReaderCalendarData
import com.mediasage.domain.model.UserSession
import com.mediasage.domain.repository.AuthRepository
import com.mediasage.domain.repository.DayAssignmentRepository
import com.mediasage.domain.usecase.GetReaderCalendarUseCase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
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
    private val authRepository: AuthRepository,
) : ViewModel() {

    private val today = Instant.fromEpochMilliseconds(epochMillis())
        .toLocalDateTime(TimeZone.currentSystemDefault()).date
    private val startOfWeek = today.minus(today.dayOfWeek.ordinal, DateTimeUnit.DAY)
    private val startOfWeekEpochDay = startOfWeek.toEpochDays().toLong()
    private val endOfWeekEpochDay =
        today.plus(DayOfWeek.SUNDAY.ordinal - today.dayOfWeek.ordinal, DateTimeUnit.DAY).toEpochDays().toLong()
    private val todayEpochDay = today.toEpochDays().toLong()

    /** The only user selection this screen owns: the open picker sheet and any pending reassignment. */
    private val input = MutableStateFlow(ScreenInput())

    private val calendarData: Flow<ReaderCalendarData> = getReaderCalendar(startOfWeekEpochDay, endOfWeekEpochDay)

    val state: StateFlow<ReaderContract.UiState> =
        combine(input, calendarData, authRepository.observeAuthState()) { screenInput, data, session ->
            buildReady(screenInput, data, session)
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            initialValue = ReaderContract.UiState.Ready(),
        )

    fun onIntent(intent: ReaderContract.Intent) {
        when (intent) {
            is ReaderContract.Intent.DaySlotTapped ->
                input.update { it.copy(activeSheet = ReaderContract.ActiveSheet.WeekSlotPicker(intent.index)) }
            is ReaderContract.Intent.PickerDismissed -> input.update { it.copy(activeSheet = null) }
            is ReaderContract.Intent.FigureAssigned -> handleFigureAssigned(intent)
            is ReaderContract.Intent.AssignmentCleared -> writeThenCloseSheet {
                dayAssignmentRepository.clear(intent.dayOfWeek)
            }
            is ReaderContract.Intent.ConfirmReassignment -> handleConfirmReassignment()
            is ReaderContract.Intent.CancelReassignment -> input.update { it.copy(pendingReassignment = null) }
        }
    }

    /** Run a repository write event, then close the open sheet once it completes. */
    private fun writeThenCloseSheet(block: suspend () -> Unit) {
        viewModelScope.launch {
            block()
            input.update { it.copy(activeSheet = null) }
        }
    }

    /** Guards today's locked-in day: a different figure requires confirmation before it is assigned. */
    private fun handleFigureAssigned(intent: ReaderContract.Intent.FigureAssigned) {
        viewModelScope.launch {
            val data = calendarData.first()
            val lockedFigureId = lockedFigureIdFor(intent.dayOfWeek, data)
            if (lockedFigureId != null && lockedFigureId != intent.figureId) {
                promptReassignment(intent, lockedFigureId, data)
            } else {
                dayAssignmentRepository.assign(intent.dayOfWeek, intent.figureId, intent.lens)
                input.update { it.copy(activeSheet = null) }
            }
        }
    }

    private fun lockedFigureIdFor(dayOfWeek: Int, data: ReaderCalendarData): Long? {
        if (dayOfWeek != today.dayOfWeek.ordinal) return null
        return data.briefingByDay[todayEpochDay]?.figureId
    }

    private fun promptReassignment(
        intent: ReaderContract.Intent.FigureAssigned,
        lockedFigureId: Long,
        data: ReaderCalendarData,
    ) {
        val figuresById = data.figures.associateBy { it.id }
        val currentName = figuresById[lockedFigureId]?.name ?: return
        val newName = figuresById[intent.figureId]?.name ?: return
        input.update {
            it.copy(
                activeSheet = null,
                pendingReassignment = ReaderContract.PendingReassignment(
                    dayOfWeek = intent.dayOfWeek,
                    figureId = intent.figureId,
                    lens = intent.lens,
                    currentFigureName = currentName,
                    newFigureName = newName,
                    nextWeekdayLabel = weekdayLabel(intent.dayOfWeek),
                ),
            )
        }
    }

    private fun handleConfirmReassignment() {
        val pending = input.value.pendingReassignment ?: return
        viewModelScope.launch {
            dayAssignmentRepository.assign(pending.dayOfWeek, pending.figureId, pending.lens)
            input.update { it.copy(pendingReassignment = null) }
        }
    }

    private fun weekdayLabel(dayOfWeek: Int): String =
        DayOfWeek.entries[dayOfWeek].name.lowercase().replaceFirstChar { it.uppercase() }

    private fun buildReady(
        screenInput: ScreenInput,
        data: ReaderCalendarData,
        session: UserSession?,
    ): ReaderContract.UiState.Ready {
        val figuresById = data.figures.associateBy { it.id }
        val quoteFigure = data.latestQuote?.let { figuresById[it.figureId] }
        return ReaderContract.UiState.Ready(
            weekSlots = buildWeekSlots(figuresById, data.assignmentsByDayOfWeek),
            pickerFigures = data.figures,
            quoteCard = buildQuoteCard(data.latestQuote, quoteFigure),
            activeSheet = screenInput.activeSheet,
            pendingReassignment = screenInput.pendingReassignment,
            userDisplayName = session?.displayName,
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

    private data class ScreenInput(
        val activeSheet: ReaderContract.ActiveSheet? = null,
        val pendingReassignment: ReaderContract.PendingReassignment? = null,
    )

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
