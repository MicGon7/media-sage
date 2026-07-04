package com.mediasage.feature.you

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mediasage.data.repository.epochMillis
import com.mediasage.domain.model.DailyReflection
import com.mediasage.domain.model.Encouragement
import com.mediasage.domain.repository.DailyReflectionRepository
import com.mediasage.domain.repository.EncouragementRepository
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
) : ViewModel() {

    private val _mode = MutableStateFlow(HistoryContract.CalendarMode.WEEK)
    private val _selectedDay = MutableStateFlow<Long?>(null)
    private val _dayDetail = MutableStateFlow<HistoryContract.DayDetail?>(null)
    private var detailJob: Job? = null

    private val _state = MutableStateFlow<HistoryContract.UiState>(HistoryContract.UiState.Loading)
    val state: StateFlow<HistoryContract.UiState> = _state.asStateFlow()

    init {
        val today = todayLocalDate()
        val yearStart = LocalDate(today.year, 1, 1).toEpochDays().toLong()
        val yearEnd = LocalDate(today.year, 12, 31).toEpochDays().toLong()
        combine(
            _mode,
            _selectedDay,
            _dayDetail,
            encouragementRepository.observeActiveEpochDays(),
            reflectionRepository.observeByEpochDayRange(yearStart, yearEnd),
        ) { mode, selectedDay, dayDetail, activeEncDays, briefingDays ->
            val activeDays = activeEncDays + briefingDays.map { it.epochDay }
            HistoryContract.UiState.Ready(
                mode = mode,
                calendarDays = buildCalendarDays(mode, todayLocalDate(), activeDays),
                selectedEpochDay = selectedDay,
                dayDetail = if (selectedDay != null) dayDetail else null,
            )
        }.onEach { _state.value = it }.launchIn(viewModelScope)
        if (initialEpochDay > 0L) selectDay(initialEpochDay)
    }

    fun onIntent(intent: HistoryContract.Intent) {
        when (intent) {
            is HistoryContract.Intent.SelectMode -> _mode.value = intent.mode
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
        activeDays: Set<Long>,
    ): List<HistoryContract.CalendarDay> = when (mode) {
        HistoryContract.CalendarMode.WEEK -> buildWeekDays(today, activeDays)
        HistoryContract.CalendarMode.MONTH -> buildMonthDays(today, activeDays)
        HistoryContract.CalendarMode.YEAR -> buildYearTiles(today, activeDays)
    }

    private fun buildWeekDays(today: LocalDate, activeDays: Set<Long>): List<HistoryContract.CalendarDay> {
        val startOfWeek = today.minus(today.dayOfWeek.ordinal, DateTimeUnit.DAY)
        return (0..6).map { i ->
            val date = startOfWeek.plus(i, DateTimeUnit.DAY)
            val epochDay = date.toEpochDays().toLong()
            HistoryContract.CalendarDay(
                epochDay = epochDay,
                label = date.dayOfWeek.name.take(3),
                isToday = date == today,
                hasData = epochDay in activeDays,
            )
        }
    }

    private fun buildMonthDays(today: LocalDate, activeDays: Set<Long>): List<HistoryContract.CalendarDay> {
        val firstOfMonth = LocalDate(today.year, today.monthNumber, 1)
        val daysInMonth = firstOfMonth.plus(1, DateTimeUnit.MONTH).minus(1, DateTimeUnit.DAY).dayOfMonth
        return (0 until daysInMonth).map { d ->
            val date = firstOfMonth.plus(d, DateTimeUnit.DAY)
            val epochDay = date.toEpochDays().toLong()
            HistoryContract.CalendarDay(
                epochDay = epochDay,
                label = "${date.dayOfMonth}",
                isToday = date == today,
                hasData = epochDay in activeDays,
            )
        }
    }

    private fun buildYearTiles(today: LocalDate, activeDays: Set<Long>): List<HistoryContract.CalendarDay> {
        return (1..12).map { month ->
            val firstOfMonth = LocalDate(today.year, month, 1)
            val startEpochDay = firstOfMonth.toEpochDays().toLong()
            val endEpochDay = firstOfMonth.plus(1, DateTimeUnit.MONTH).toEpochDays().toLong()
            HistoryContract.CalendarDay(
                epochDay = startEpochDay,
                label = firstOfMonth.month.name.take(3),
                isToday = firstOfMonth.monthNumber == today.monthNumber,
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
