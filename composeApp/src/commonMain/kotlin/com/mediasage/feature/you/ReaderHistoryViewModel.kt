@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.mediasage.feature.you

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mediasage.data.repository.epochMillis
import com.mediasage.domain.model.BriefingDay
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
 * The calendar view renders every month from the earliest recorded briefing through the current
 * month as a single scrollable list (most recent first), all derived from one full-range fetch —
 * there is no per-month paging or lazy loading to coordinate.
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
    private val currentMonthEndEpochDay = LocalDate(today.year, today.monthNumber, 1)
        .plus(1, DateTimeUnit.MONTH).toEpochDays().toLong() - 1

    private val viewMode = MutableStateFlow(ReaderHistoryContract.ViewMode.LIST)

    /** The earliest day with a real briefing, resolved once. Falls back to today when there is none. */
    private val earliestEpochDay: Flow<Long> = flow {
        val earliest = reflectionRepository.getEarliestBriefingEpochDay() ?: todayEpochDay
        emit(minOf(earliest, todayEpochDay))
    }

    /** Earliest day paired with the calendar material for the entire bounded history. */
    private val fullRangeCalendarData: Flow<Pair<Long, ReaderCalendarData>> =
        earliestEpochDay.flatMapLatest { earliest -> getReaderCalendar(earliest, todayEpochDay).map { earliest to it } }

    val state: StateFlow<ReaderHistoryContract.UiState> =
        combine(viewMode, fullRangeCalendarData) { mode, (earliest, data) ->
            buildReady(mode, data, earliest)
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
            is ReaderHistoryContract.Intent.ViewModeChanged -> viewMode.update { intent.viewMode }
        }
    }

    private fun buildReady(
        mode: ReaderHistoryContract.ViewMode,
        data: ReaderCalendarData,
        earliestEpochDay: Long,
    ): ReaderHistoryContract.UiState.Ready = ReaderHistoryContract.UiState.Ready(
        todayEpochDay = todayEpochDay,
        earliestEpochDay = earliestEpochDay,
        viewMode = mode,
        calendarMonths = buildCalendarMonths(earliestEpochDay, data),
        listDays = buildListDays(earliestEpochDay, data),
    )

    /**
     * Builds every full calendar month from the month containing [earliestEpochDay] through the
     * current month, grouped and ordered most-recent-first for the scrollable Calendar view. Each
     * month is generated in full (day 1 through its last day) — not clipped to the earliest/today
     * bounds — so the weekday grid always aligns correctly; days outside the real data range simply
     * resolve to `hasData = false`.
     */
    private fun buildCalendarMonths(
        earliestEpochDay: Long,
        data: ReaderCalendarData,
    ): List<List<ReaderHistoryContract.CalendarDay>> {
        val figuresById = data.figures.associateBy { it.id }
        val earliestMonthStart = LocalDate.fromEpochDays(earliestEpochDay.toInt())
            .let { LocalDate(it.year, it.monthNumber, 1) }.toEpochDays().toLong()
        val days = (earliestMonthStart..currentMonthEndEpochDay).map { epochDay ->
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
        return days
            .groupBy { LocalDate.fromEpochDays(it.epochDay.toInt()).let { d -> d.year to d.monthNumber } }
            .values.toList().asReversed()
    }

    private fun buildListDays(
        earliestEpochDay: Long,
        data: ReaderCalendarData,
    ): List<ReaderHistoryContract.ListDay> {
        val figuresById = data.figures.associateBy { it.id }
        return (earliestEpochDay..todayEpochDay).mapNotNull { epochDay ->
            val figureId = resolveFigureId(epochDay, data.briefingByDay, data.assignmentsByDayOfWeek)
            val figure = figureId?.let { figuresById[it] } ?: return@mapNotNull null
            val briefing = data.briefingByDay[epochDay]
            ReaderHistoryContract.ListDay(
                epochDay = epochDay,
                figurePortraitUrl = figure.portraitUrl,
                figureName = figure.name,
                scriptureReference = briefing?.scriptureReference,
                scriptureText = briefing?.scriptureText,
            )
        }.sortedByDescending { it.epochDay }
    }

    /**
     * Past days, and today once its briefing has been generated, show the reporter whose briefing
     * actually ran — once generated, today's reporter is locked and no longer tracks the recurring
     * schedule (see [com.mediasage.domain.repository.DayAssignmentRepository.resolveReporter]).
     * Before today's briefing exists yet, it falls back to the reporter the recurring schedule
     * currently resolves to. Future days never resolve to a reporter here — showing the
     * recurring-schedule preview as if it were settled history is exactly the ambiguity this screen
     * exists to avoid.
     */
    private fun resolveFigureId(
        epochDay: Long,
        briefingByDay: Map<Long, BriefingDay>,
        assignmentsByDayOfWeek: Map<Int, DayAssignment>,
    ): Long? = when {
        epochDay > todayEpochDay -> null
        epochDay == todayEpochDay ->
            briefingByDay[epochDay]?.figureId
                ?: assignmentsByDayOfWeek[LocalDate.fromEpochDays(epochDay.toInt()).dayOfWeek.ordinal]?.figureId
        else -> briefingByDay[epochDay]?.figureId
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
