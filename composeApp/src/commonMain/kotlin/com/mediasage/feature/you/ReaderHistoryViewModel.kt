@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.mediasage.feature.you

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mediasage.data.repository.epochMillis
import com.mediasage.domain.model.DayAssignment
import com.mediasage.domain.model.Figure
import com.mediasage.domain.model.ReaderCalendarData
import com.mediasage.domain.repository.DailyReflectionRepository
import com.mediasage.domain.usecase.GetReaderCalendarUseCase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

/**
 * Read-only, past-and-today browse of Reader history — the dedicated screen this data feeds is
 * reached from a card on the main Reader screen (see [ReaderViewModel], which owns only the
 * recurring weekly schedule).
 *
 * Reuses [GetReaderCalendarUseCase] — the same domain stream [ReaderViewModel] used before this
 * screen was split out — but never touches `DayAssignmentRepository`'s write methods: this screen
 * has no way to assign or change a reporter.
 *
 * The lower bound of both the calendar and list views comes from [DailyReflectionRepository]'s
 * earliest recorded briefing, not a hardcoded release date — a month with no briefings is never
 * reachable.
 *
 * Tapping a day navigates straight to the pushed day-detail screen (which owns its own
 * `GetDayDetailUseCase` fetch); this ViewModel only ever needs to know which reporter's portrait to
 * show on each calendar cell or list row.
 *
 * Future days are never populated from the recurring schedule here. That blending of "planned" and
 * "happened" on one visual was the ambiguity this screen exists to remove — the schedule preview
 * stays on the Reader week strip only.
 */
class ReaderHistoryViewModel(
    private val getReaderCalendar: GetReaderCalendarUseCase,
    private val reflectionRepository: DailyReflectionRepository,
) : ViewModel() {

    private val today = Instant.fromEpochMilliseconds(epochMillis())
        .toLocalDateTime(TimeZone.currentSystemDefault()).date
    private val todayEpochDay = today.toEpochDays().toLong()

    private val input = MutableStateFlow(
        HistoryInput(visibleMonth = LocalDate(today.year, today.monthNumber, 1)),
    )

    /** The earliest day with a real briefing, resolved once. Falls back to today when there is none. */
    private val earliestEpochDay: Flow<Long> = flow {
        val earliest = reflectionRepository.getEarliestBriefingEpochDay() ?: todayEpochDay
        emit(minOf(earliest, todayEpochDay))
    }

    /** Calendar material for the visible month. Restarts only when the month changes. */
    private val monthCalendarData: Flow<ReaderCalendarData> =
        input.map { it.visibleMonth }.distinctUntilChanged().flatMapLatest { month ->
            val range = monthRange(month)
            getReaderCalendar(range.monthStart, range.monthEnd)
        }

    /** Earliest day paired with the calendar material for the entire bounded history. */
    private val fullRangeCalendarData: Flow<Pair<Long, ReaderCalendarData>> =
        earliestEpochDay.flatMapLatest { earliest -> getReaderCalendar(earliest, todayEpochDay).map { earliest to it } }

    val state: StateFlow<ReaderHistoryContract.UiState> =
        combine(input, monthCalendarData, fullRangeCalendarData) { input, monthData, (earliest, fullData) ->
            buildReady(input, monthData, fullData, earliest)
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            initialValue = ReaderHistoryContract.UiState.Ready(
                todayEpochDay = todayEpochDay,
                earliestEpochDay = todayEpochDay,
            ),
        )

    fun onIntent(intent: ReaderHistoryContract.Intent) {
        when (intent) {
            is ReaderHistoryContract.Intent.MonthPageChanged ->
                input.update { it.copy(visibleMonth = LocalDate(intent.year, intent.month, 1)) }
            is ReaderHistoryContract.Intent.ViewModeChanged ->
                input.update { it.copy(viewMode = intent.viewMode) }
        }
    }

    private fun buildReady(
        input: HistoryInput,
        monthData: ReaderCalendarData,
        fullData: ReaderCalendarData,
        earliestEpochDay: Long,
    ): ReaderHistoryContract.UiState.Ready {
        val monthFiguresById = monthData.figures.associateBy { it.id }
        val range = monthRange(input.visibleMonth)
        val daysInMonth = (range.monthEnd - range.monthStart + 1).toInt()
        return ReaderHistoryContract.UiState.Ready(
            todayEpochDay = todayEpochDay,
            earliestEpochDay = earliestEpochDay,
            viewMode = input.viewMode,
            calendarDays = buildCalendarDays(range.monthStart, daysInMonth, monthFiguresById, monthData),
            listDays = buildListDays(earliestEpochDay, fullData),
        )
    }

    private fun buildCalendarDays(
        monthStartEpoch: Long,
        daysInMonth: Int,
        figuresById: Map<Long, Figure>,
        data: ReaderCalendarData,
    ): List<ReaderHistoryContract.CalendarDay> = (0 until daysInMonth).map { d ->
        val epochDay = monthStartEpoch + d
        val figureId = resolveFigureId(epochDay, data.briefingByDay, data.assignmentsByDayOfWeek)
        val figure = figureId?.let { figuresById[it] }
        ReaderHistoryContract.CalendarDay(
            epochDay = epochDay,
            dateNumber = LocalDate.fromEpochDays(epochDay.toInt()).dayOfMonth,
            isToday = epochDay == todayEpochDay,
            isFuture = epochDay > todayEpochDay,
            hasData = figureId != null,
            figurePortraitUrl = figure?.portraitUrl,
            figureName = figure?.name,
        )
    }

    private fun buildListDays(
        earliestEpochDay: Long,
        data: ReaderCalendarData,
    ): List<ReaderHistoryContract.ListDay> {
        val figuresById = data.figures.associateBy { it.id }
        return (earliestEpochDay..todayEpochDay).mapNotNull { epochDay ->
            val figureId = resolveFigureId(epochDay, data.briefingByDay, data.assignmentsByDayOfWeek)
            val figure = figureId?.let { figuresById[it] } ?: return@mapNotNull null
            ReaderHistoryContract.ListDay(
                epochDay = epochDay,
                figurePortraitUrl = figure.portraitUrl,
                figureName = figure.name,
            )
        }.sortedByDescending { it.epochDay }
    }

    /**
     * Past days show the reporter whose briefing actually ran. Today shows the reporter the
     * recurring schedule currently resolves to, since today's content is generated from it. Future
     * days never resolve to a reporter here — showing the recurring-schedule preview as if it were
     * settled history is exactly the ambiguity this screen exists to avoid.
     */
    private fun resolveFigureId(
        epochDay: Long,
        briefingByDay: Map<Long, Long>,
        assignmentsByDayOfWeek: Map<Int, DayAssignment>,
    ): Long? = when {
        epochDay > todayEpochDay -> null
        epochDay == todayEpochDay ->
            assignmentsByDayOfWeek[LocalDate.fromEpochDays(epochDay.toInt()).dayOfWeek.ordinal]?.figureId
        else -> briefingByDay[epochDay]
    }

    private fun monthRange(month: LocalDate): MonthRange {
        val start = month.toEpochDays().toLong()
        val end = month.plus(1, DateTimeUnit.MONTH).toEpochDays().toLong() - 1
        return MonthRange(monthStart = start, monthEnd = end)
    }

    /** The user-owned view selection — the single mutable input to the state pipeline. */
    private data class HistoryInput(
        val visibleMonth: LocalDate,
        val viewMode: ReaderHistoryContract.ViewMode = ReaderHistoryContract.ViewMode.CALENDAR,
    )

    private data class MonthRange(
        val monthStart: Long,
        val monthEnd: Long,
    )

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
