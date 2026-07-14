@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.mediasage.feature.you

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mediasage.data.repository.epochMillis
import com.mediasage.domain.model.CalendarData
import com.mediasage.domain.model.DailyReflection
import com.mediasage.domain.model.Encouragement
import com.mediasage.domain.model.Figure
import com.mediasage.domain.repository.DailyReflectionRepository
import com.mediasage.domain.repository.EncouragementRepository
import com.mediasage.domain.usecase.ObserveCalendarDataUseCase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

/**
 * Reference implementation of the Now in Android state-holder pattern for UI state derived from
 * both user selection and live repository streams (see composeApp/CLAUDE.md, "State-holder pattern").
 *
 * The user's view selection lives in a single [CalendarInput] flow. It is combined with the
 * calendar domain stream (from [ObserveCalendarDataUseCase]) and the selected-day detail stream to
 * derive [HistoryContract.UiState], exposed via `stateIn`. UiState is the *output* of that pipeline
 * — intents update [input]; nothing writes to the state directly.
 */
class HistoryViewModel(
    initialEpochDay: Long,
    private val observeCalendarData: ObserveCalendarDataUseCase,
    private val reflectionRepository: DailyReflectionRepository,
    private val encouragementRepository: EncouragementRepository,
) : ViewModel() {

    /** The user-owned view selection — the single mutable input to the state pipeline. */
    private data class CalendarInput(
        val mode: HistoryContract.CalendarMode = HistoryContract.CalendarMode.WEEK,
        val selectedTab: HistoryContract.DayTab = HistoryContract.DayTab.BRIEFING,
        val selectedEpochDay: Long? = null,
        val anchor: LocalDate,
    )

    private val today = todayLocalDate()

    private val input = MutableStateFlow(
        CalendarInput(
            selectedEpochDay = initialEpochDay.takeIf { it > 0L },
            anchor = if (initialEpochDay > 0L) LocalDate.fromEpochDays(initialEpochDay.toInt()) else today,
        ),
    )

    /** Detail for the selected day. flatMapLatest cancels the prior day's collection automatically. */
    private val dayDetail: Flow<HistoryContract.DayDetail?> =
        input.map { it.selectedEpochDay }
            .distinctUntilChanged()
            .flatMapLatest { epochDay -> if (epochDay == null) flowOf(null) else observeDayDetail(epochDay) }

    val state: StateFlow<HistoryContract.UiState> =
        combine(
            input,
            observeCalendarData(yearStart(), yearEnd()),
            dayDetail,
        ) { input, data, detail ->
            buildReady(input, data, detail)
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            initialValue = HistoryContract.UiState.Loading,
        )

    fun onIntent(intent: HistoryContract.Intent) {
        when (intent) {
            is HistoryContract.Intent.SelectMode -> input.update { it.copy(mode = intent.mode) }
            is HistoryContract.Intent.SelectTab -> input.update { it.copy(selectedTab = intent.tab) }
            is HistoryContract.Intent.SelectDay -> input.update {
                it.copy(
                    selectedEpochDay = intent.epochDay,
                    anchor = LocalDate.fromEpochDays(intent.epochDay.toInt()),
                    selectedTab = HistoryContract.DayTab.BRIEFING,
                )
            }
            is HistoryContract.Intent.ClearSelection -> input.update { it.copy(selectedEpochDay = null) }
            is HistoryContract.Intent.ToggleBookmark -> viewModelScope.launch {
                encouragementRepository.toggleBookmark(intent.articleUrl)
            }
        }
    }

    private fun observeDayDetail(epochDay: Long): Flow<HistoryContract.DayDetail> = flow {
        val reflection = reflectionRepository.getForDay(epochDay)
        emitAll(
            encouragementRepository.observeByEpochDay(epochDay).map { encouragements ->
                HistoryContract.DayDetail(
                    epochDay = epochDay,
                    reflection = reflection?.toSummary(),
                    encouragements = encouragements.map { it.toItem() },
                )
            },
        )
    }

    private fun buildReady(
        input: CalendarInput,
        data: CalendarData,
        detail: HistoryContract.DayDetail?,
    ): HistoryContract.UiState.Ready {
        val figure = input.selectedEpochDay
            ?.let { data.briefingByDay[it] }
            ?.let { data.figuresById[it] }
        return HistoryContract.UiState.Ready(
            mode = input.mode,
            selectedTab = input.selectedTab,
            calendarDays = buildCalendarDays(input.mode, input.anchor, data),
            selectedEpochDay = input.selectedEpochDay,
            dayDetail = detail?.copy(figureName = figure?.name, figureImageUrl = figure?.portraitUrl),
        )
    }

    private fun buildCalendarDays(
        mode: HistoryContract.CalendarMode,
        anchor: LocalDate,
        data: CalendarData,
    ): List<HistoryContract.CalendarDay> = when (mode) {
        HistoryContract.CalendarMode.WEEK -> buildWeekDays(anchor, data)
        HistoryContract.CalendarMode.MONTH -> buildMonthDays(anchor, data)
        HistoryContract.CalendarMode.YEAR -> buildYearTiles(data.activeDays)
    }

    private fun buildWeekDays(anchor: LocalDate, data: CalendarData): List<HistoryContract.CalendarDay> {
        val startOfWeek = anchor.minus(anchor.dayOfWeek.ordinal, DateTimeUnit.DAY)
        val todayEpochDay = today.toEpochDays()
        return (0 until DAYS_IN_WEEK).map { offset ->
            val date = startOfWeek.plus(offset, DateTimeUnit.DAY)
            calendarDay(date, date.dayOfWeek.name.take(LABEL_LENGTH), todayEpochDay, data)
        }
    }

    private fun buildMonthDays(anchor: LocalDate, data: CalendarData): List<HistoryContract.CalendarDay> {
        val firstOfMonth = LocalDate(anchor.year, anchor.month, 1)
        val daysInMonth = firstOfMonth.plus(1, DateTimeUnit.MONTH).minus(1, DateTimeUnit.DAY).day
        val todayEpochDay = today.toEpochDays()
        return (0 until daysInMonth).map { offset ->
            val date = firstOfMonth.plus(offset, DateTimeUnit.DAY)
            calendarDay(date, "${date.day}", todayEpochDay, data)
        }
    }

    private fun calendarDay(
        date: LocalDate,
        label: String,
        todayEpochDay: Long,
        data: CalendarData,
    ): HistoryContract.CalendarDay {
        val epochDay = date.toEpochDays()
        val isFuture = epochDay > todayEpochDay
        val figure = data.briefingByDay[epochDay]?.let { data.figuresById[it] }
            ?: futureAssignmentFigure(date, epochDay, isFuture, todayEpochDay, data)
        return HistoryContract.CalendarDay(
            epochDay = epochDay,
            label = label,
            isToday = date == today,
            hasData = epochDay in data.activeDays,
            isFuture = isFuture,
            figurePortraitUrl = figure?.portraitUrl,
            figureName = figure?.name,
        )
    }

    private fun futureAssignmentFigure(
        date: LocalDate,
        epochDay: Long,
        isFuture: Boolean,
        todayEpochDay: Long,
        data: CalendarData,
    ): Figure? {
        if (!isFuture || epochDay > todayEpochDay + DAYS_IN_WEEK) return null
        return data.assignmentsByDayOfWeek[date.dayOfWeek.ordinal]?.let { data.figuresById[it.figureId] }
    }

    private fun buildYearTiles(activeDays: Set<Long>): List<HistoryContract.CalendarDay> =
        (1..MONTHS_IN_YEAR).map { month ->
            val firstOfMonth = LocalDate(today.year, month, 1)
            val startEpochDay = firstOfMonth.toEpochDays()
            val endEpochDay = firstOfMonth.plus(1, DateTimeUnit.MONTH).toEpochDays()
            HistoryContract.CalendarDay(
                epochDay = startEpochDay,
                label = firstOfMonth.month.name.take(LABEL_LENGTH),
                isToday = firstOfMonth.month == today.month,
                hasData = activeDays.any { it in startEpochDay until endEpochDay },
            )
        }

    private fun yearStart(): Long = LocalDate(today.year, 1, 1).toEpochDays()
    private fun yearEnd(): Long = LocalDate(today.year, 12, 31).toEpochDays()

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
        const val DAYS_IN_WEEK = 7
        const val MONTHS_IN_YEAR = 12
        const val LABEL_LENGTH = 3
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
