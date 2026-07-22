@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.mediasage.feature.you

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mediasage.data.repository.epochMillis
import com.mediasage.domain.model.DailyReflection
import com.mediasage.domain.model.DayAssignment
import com.mediasage.domain.model.DayDetailData
import com.mediasage.domain.model.Encouragement
import com.mediasage.domain.model.Figure
import com.mediasage.domain.model.Quote
import com.mediasage.domain.model.ReaderCalendarData
import com.mediasage.domain.repository.DayAssignmentRepository
import com.mediasage.domain.usecase.GetDayDetailUseCase
import com.mediasage.domain.usecase.GetReaderCalendarUseCase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
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
 * The user's view selection (visible month, calendar expansion, open sheet) lives in a single
 * [ReaderInput] flow. It is combined with the calendar domain stream ([GetReaderCalendarUseCase])
 * and the selected-day detail stream to derive [ReaderContract.UiState], exposed via `stateIn`.
 * `UiState` is the *output* of that pipeline — intents update [input]; nothing writes state directly.
 */
class ReaderViewModel(
    private val getReaderCalendar: GetReaderCalendarUseCase,
    private val getDayDetail: GetDayDetailUseCase,
    private val dayAssignmentRepository: DayAssignmentRepository,
) : ViewModel() {

    private val today = Instant.fromEpochMilliseconds(epochMillis())
        .toLocalDateTime(TimeZone.currentSystemDefault()).date
    private val todayEpochDay = today.toEpochDays().toLong()

    /** Last day of the current week — the weekly-assignment fallback only reaches this far, matching the carousel. */
    private val endOfWeekEpochDay =
        today.plus(DayOfWeek.SUNDAY.ordinal - today.dayOfWeek.ordinal, DateTimeUnit.DAY).toEpochDays().toLong()

    private val input = MutableStateFlow(
        ReaderInput(visibleMonth = LocalDate(today.year, today.monthNumber, 1)),
    )

    /** Calendar material for the visible month. Restarts only when the month changes. */
    private val calendarData: Flow<ReaderCalendarData> =
        input.map { it.visibleMonth }.distinctUntilChanged().flatMapLatest { month ->
            val range = monthRange(month)
            getReaderCalendar(range.monthStart, range.monthEnd, range.overrideStart, range.overrideEnd)
        }

    /** Detail for the open history day. flatMapLatest cancels the prior day's collection automatically. */
    private val dayDetail: Flow<ReaderContract.DayDetail?> =
        input.map { (it.activeSheet as? SheetSelection.History)?.epochDay }
            .distinctUntilChanged()
            .flatMapLatest { epochDay ->
                if (epochDay == null) flowOf(null)
                else getDayDetail(epochDay).map { it.toReaderDetail(epochDay) }
            }

    val state: StateFlow<ReaderContract.UiState> =
        combine(input, calendarData, dayDetail) { input, data, detail ->
            buildReady(input, data, detail)
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            initialValue = ReaderContract.UiState.Ready(),
        )

    fun onIntent(intent: ReaderContract.Intent) {
        when (intent) {
            is ReaderContract.Intent.DaySlotTapped -> input.update { it.copy(activeSheet = SheetSelection.WeekSlot(intent.index)) }
            is ReaderContract.Intent.PickerDismissed -> input.update { it.copy(activeSheet = null) }
            is ReaderContract.Intent.FigureAssigned -> writeThenCloseSheet {
                dayAssignmentRepository.assign(intent.dayOfWeek, intent.figureId, intent.lens)
            }
            is ReaderContract.Intent.AssignmentCleared -> writeThenCloseSheet {
                dayAssignmentRepository.clear(intent.dayOfWeek)
            }
            is ReaderContract.Intent.ToggleCalendarExpanded -> input.update { it.copy(isCalendarExpanded = !it.isCalendarExpanded) }
            is ReaderContract.Intent.MonthPageChanged -> input.update { it.copy(visibleMonth = LocalDate(intent.year, intent.month, 1)) }
            is ReaderContract.Intent.HistoryDayTapped -> input.update { it.copy(activeSheet = SheetSelection.History(intent.epochDay)) }
        }
    }

    /** Run a repository write event, then close the open sheet once it completes. */
    private fun writeThenCloseSheet(block: suspend () -> Unit) {
        viewModelScope.launch {
            block()
            input.update { it.copy(activeSheet = null) }
        }
    }

    private fun buildReady(
        input: ReaderInput,
        data: ReaderCalendarData,
        detail: ReaderContract.DayDetail?,
    ): ReaderContract.UiState.Ready {
        val figuresById = data.figures.associateBy { it.id }
        val range = monthRange(input.visibleMonth)
        val daysInMonth = (range.monthEnd - range.monthStart + 1).toInt()
        val quoteFigure = data.latestQuote?.let { figuresById[it.figureId] }
        return ReaderContract.UiState.Ready(
            weekSlots = buildWeekSlots(figuresById, data.assignmentsByDayOfWeek),
            pickerFigures = data.figures,
            quoteCard = buildQuoteCard(data.latestQuote, quoteFigure),
            calendarDays = buildCalendarDays(
                range.monthStart,
                daysInMonth,
                figuresById,
                data.briefingByDay,
                data.assignmentsByDayOfWeek,
            ),
            isCalendarExpanded = input.isCalendarExpanded,
            activeSheet = buildActiveSheet(input.activeSheet, figuresById, data, detail),
        )
    }

    private fun buildWeekSlots(
        figuresById: Map<Long, Figure>,
        assignments: Map<Int, DayAssignment>,
    ): List<ReaderContract.DaySlot> {
        val startOfWeek = today.minus(today.dayOfWeek.ordinal, DateTimeUnit.DAY)
        return (0..6).map { i ->
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
    }

    /**
     * Precedence per day cell mirrors [buildWeekSlots] for the current week, so the month grid and
     * the week carousel agree on the days the carousel actually shows. The weekly-assignment
     * fallback only reaches through the end of this week — future days beyond it show nothing.
     * Past days ignore the schedule and show only the briefing that actually ran.
     */
    private fun buildCalendarDays(
        monthStartEpoch: Long,
        daysInMonth: Int,
        figuresById: Map<Long, Figure>,
        briefingByDay: Map<Long, Long>,
        assignmentsByDayOfWeek: Map<Int, DayAssignment>,
    ): List<ReaderContract.CalendarDay> = (0 until daysInMonth).map { d ->
        val epochDay = monthStartEpoch + d
        val date = LocalDate.fromEpochDays(epochDay.toInt())
        val isFuture = epochDay > todayEpochDay
        val weeklyFigureId = if (epochDay in todayEpochDay..endOfWeekEpochDay) {
            assignmentsByDayOfWeek[date.dayOfWeek.ordinal]?.figureId
        } else {
            null
        }
        val figureId = if (epochDay < todayEpochDay) briefingByDay[epochDay] else weeklyFigureId
        val figure = figureId?.let { figuresById[it] }
        ReaderContract.CalendarDay(
            epochDay = epochDay,
            dateNumber = date.dayOfMonth,
            isToday = epochDay == todayEpochDay,
            isFuture = isFuture,
            hasData = figureId != null,
            figurePortraitUrl = figure?.portraitUrl,
            figureName = figure?.name,
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

    private fun buildActiveSheet(
        selection: SheetSelection?,
        figuresById: Map<Long, Figure>,
        data: ReaderCalendarData,
        detail: ReaderContract.DayDetail?,
    ): ReaderContract.ActiveSheet? = when (selection) {
        null -> null
        is SheetSelection.WeekSlot -> ReaderContract.ActiveSheet.WeekSlotPicker(selection.dayOfWeek)
        is SheetSelection.History ->
            ReaderContract.ActiveSheet.HistoryDetail(buildDayDetail(selection.epochDay, figuresById, data, detail))
    }

    private fun buildDayDetail(
        epochDay: Long,
        figuresById: Map<Long, Figure>,
        data: ReaderCalendarData,
        detail: ReaderContract.DayDetail?,
    ): ReaderContract.DayDetail {
        val figureId = data.briefingByDay[epochDay]
        val figure = figureId?.let { figuresById[it] }
        return ReaderContract.DayDetail(
            epochDay = epochDay,
            reflection = detail?.reflection,
            articles = detail?.articles ?: emptyList(),
            figureName = figure?.name,
            figureImageUrl = figure?.portraitUrl,
        )
    }

    private fun monthRange(month: LocalDate): MonthRange {
        val start = month.toEpochDays().toLong()
        val end = month.plus(1, DateTimeUnit.MONTH).toEpochDays().toLong() - 1
        return MonthRange(
            monthStart = start,
            monthEnd = end,
            overrideStart = minOf(start, todayEpochDay - WEEK_WINDOW_DAYS),
            overrideEnd = maxOf(end, todayEpochDay + WEEK_WINDOW_DAYS),
        )
    }

    /** The user-owned view selection — the single mutable input to the state pipeline. */
    private data class ReaderInput(
        val visibleMonth: LocalDate,
        val isCalendarExpanded: Boolean = false,
        val activeSheet: SheetSelection? = null,
    )

    /** Which sheet the user opened — pure selection; loaded content is derived, never stored here. */
    private sealed interface SheetSelection {
        data class WeekSlot(val dayOfWeek: Int) : SheetSelection
        data class History(val epochDay: Long) : SheetSelection
    }

    private data class MonthRange(
        val monthStart: Long,
        val monthEnd: Long,
        val overrideStart: Long,
        val overrideEnd: Long,
    )

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
        const val WEEK_WINDOW_DAYS = 7L
    }
}

private fun DayDetailData.toReaderDetail(epochDay: Long) = ReaderContract.DayDetail(
    epochDay = epochDay,
    reflection = reflection?.toReaderSummary(),
    articles = encouragements.map { it.toArticleItem() },
    figureName = null,
    figureImageUrl = null,
)

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
