@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.mediasage.feature.you

import com.mediasage.data.repository.epochMillis
import com.mediasage.domain.model.BriefingDay
import com.mediasage.domain.model.DailyReflection
import com.mediasage.domain.model.DayAssignment
import com.mediasage.domain.model.Figure
import com.mediasage.domain.model.FigureCategory
import com.mediasage.domain.model.LensFilter
import com.mediasage.domain.model.Quote
import com.mediasage.domain.repository.DailyReflectionRepository
import com.mediasage.domain.repository.DayAssignmentRepository
import com.mediasage.domain.repository.FigureRepository
import com.mediasage.domain.repository.QuoteRepository
import com.mediasage.domain.usecase.GetReaderCalendarUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Instant

class ReaderHistoryViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    private val today = Instant.fromEpochMilliseconds(epochMillis())
        .toLocalDateTime(TimeZone.currentSystemDefault()).date

    private val testFigure = Figure(
        id = 1L,
        name = "Augustine of Hippo",
        category = FigureCategory.CHURCH_FATHER,
        century = "4th",
        role = "Bishop of Hippo",
    )

    private val otherFigure = Figure(
        id = 2L,
        name = "C.S. Lewis",
        category = FigureCategory.THEOLOGIAN,
        century = "20th",
        role = "Author",
    )

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun currentMonthCalendarStartsAtDayOne() = runTest(testDispatcher) {
        val viewModel = historyViewModel()

        val state = viewModel.state.value as ReaderHistoryContract.UiState.Ready
        val currentMonth = state.calendarMonths.first()
        assertTrue(currentMonth.isNotEmpty())
        assertEquals(1, currentMonth.first().dateNumber)
        assertEquals(today.monthNumber, LocalDate.fromEpochDays(currentMonth.first().epochDay.toInt()).monthNumber)
    }

    @Test
    fun calendarMonthsAreOrderedMostRecentFirst() = runTest(testDispatcher) {
        val pastEpochDay = today.toEpochDays().toLong() - 40
        val viewModel = historyViewModel(briefings = listOf(BriefingDay(pastEpochDay, testFigure.id)))

        val state = viewModel.state.value as ReaderHistoryContract.UiState.Ready
        assertTrue(state.calendarMonths.size >= 2)
        val firstMonthDate = LocalDate.fromEpochDays(state.calendarMonths.first().first().epochDay.toInt())
        val lastMonthDate = LocalDate.fromEpochDays(state.calendarMonths.last().first().epochDay.toInt())
        assertTrue(firstMonthDate > lastMonthDate)
    }

    @Test
    fun todayShowsCurrentWeeklyAssignment() = runTest(testDispatcher) {
        val assignments = (0..6).associateWith { DayAssignment(testFigure.id, null) }
        val viewModel = historyViewModel(assignments = assignments)

        val state = viewModel.state.value as ReaderHistoryContract.UiState.Ready
        val todayCell = state.calendarMonths.first().first { it.isToday }
        assertTrue(todayCell.hasData)
        assertEquals(testFigure.name, todayCell.figureName)
    }

    @Test
    fun todayPrefersActualBriefingOverANewerConflictingAssignment() = runTest(testDispatcher) {
        // Regression test for the MS-658 follow-up bug: once today is locked to a figure, a later
        // weekday reassignment must not make Past Briefings show the newly-assigned figure instead
        // of whoever's briefing actually ran today.
        val probe = historyViewModel()
        val todayEpochDay = (probe.state.value as ReaderHistoryContract.UiState.Ready).todayEpochDay
        val assignments = (0..6).associateWith { DayAssignment(otherFigure.id, null) }
        val viewModel = historyViewModel(
            assignments = assignments,
            briefings = listOf(BriefingDay(todayEpochDay, testFigure.id)),
            figures = listOf(testFigure, otherFigure),
        )

        val state = viewModel.state.value as ReaderHistoryContract.UiState.Ready
        val todayRow = state.listDays.first { it.epochDay == todayEpochDay }
        assertEquals(testFigure.name, todayRow.figureName)
    }

    @Test
    fun futureDaysNeverShowData() = runTest(testDispatcher) {
        val assignments = (0..6).associateWith { DayAssignment(testFigure.id, null) }
        val viewModel = historyViewModel(assignments = assignments)

        val state = viewModel.state.value as ReaderHistoryContract.UiState.Ready
        val futureDay = state.calendarMonths.flatten().firstOrNull { it.isFuture } ?: return@runTest
        assertFalse(futureDay.hasData)
        assertEquals(null, futureDay.figureName)
    }

    @Test
    fun pastDayIgnoresWeeklyAssignmentWhenNoBriefingRan() = runTest(testDispatcher) {
        val assignments = (0..6).associateWith { DayAssignment(testFigure.id, null) }
        val viewModel = historyViewModel(assignments = assignments)

        val state = viewModel.state.value as ReaderHistoryContract.UiState.Ready
        val pastDay = state.calendarMonths.flatten().firstOrNull { !it.isFuture && !it.isToday } ?: return@runTest
        assertFalse(pastDay.hasData)
        assertEquals(null, pastDay.figureName)
    }

    @Test
    fun pastDayShowsBriefingReporterThatActuallyRan() = runTest(testDispatcher) {
        val probe = historyViewModel()
        val pastEpochDay = (probe.state.value as ReaderHistoryContract.UiState.Ready)
            .calendarMonths.flatten().firstOrNull { !it.isFuture && !it.isToday }?.epochDay ?: return@runTest
        val viewModel = historyViewModel(briefings = listOf(BriefingDay(pastEpochDay, testFigure.id)))

        val state = viewModel.state.value as ReaderHistoryContract.UiState.Ready
        val cell = state.calendarMonths.flatten().first { it.epochDay == pastEpochDay }
        assertTrue(cell.hasData)
        assertEquals(testFigure.name, cell.figureName)
    }

    @Test
    fun earliestEpochDayDefaultsToTodayWhenNoBriefingsExist() = runTest(testDispatcher) {
        val viewModel = historyViewModel()

        val state = viewModel.state.value as ReaderHistoryContract.UiState.Ready
        assertEquals(state.todayEpochDay, state.earliestEpochDay)
    }

    @Test
    fun earliestEpochDayReflectsEarliestBriefing() = runTest(testDispatcher) {
        val pastEpochDay = today.toEpochDays().toLong() - 5
        val viewModel = historyViewModel(briefings = listOf(BriefingDay(pastEpochDay, testFigure.id)))

        val state = viewModel.state.value as ReaderHistoryContract.UiState.Ready
        assertEquals(pastEpochDay, state.earliestEpochDay)
    }

    @Test
    fun defaultViewModeIsList() = runTest(testDispatcher) {
        val viewModel = historyViewModel()

        val state = viewModel.state.value as ReaderHistoryContract.UiState.Ready
        assertEquals(ReaderHistoryContract.ViewMode.LIST, state.viewMode)
    }

    @Test
    fun viewModeChangedIntentSwitchesToCalendar() = runTest(testDispatcher) {
        val viewModel = historyViewModel()

        viewModel.onIntent(ReaderHistoryContract.Intent.ViewModeChanged(ReaderHistoryContract.ViewMode.CALENDAR))

        val state = viewModel.state.value as ReaderHistoryContract.UiState.Ready
        assertEquals(ReaderHistoryContract.ViewMode.CALENDAR, state.viewMode)
    }

    @Test
    fun listDaysIncludesOnlyDaysWithData() = runTest(testDispatcher) {
        val pastEpochDay = today.toEpochDays().toLong() - 5
        val assignments = (0..6).associateWith { DayAssignment(testFigure.id, null) }
        val viewModel = historyViewModel(
            assignments = assignments,
            briefings = listOf(BriefingDay(pastEpochDay, testFigure.id)),
        )

        val state = viewModel.state.value as ReaderHistoryContract.UiState.Ready
        assertTrue(state.listDays.any { it.epochDay == pastEpochDay })
        assertTrue(state.listDays.any { it.epochDay == state.todayEpochDay })
        assertEquals(state.listDays, state.listDays.sortedByDescending { it.epochDay })
    }

    @Test
    fun listDayCarriesScriptureFromBriefing() = runTest(testDispatcher) {
        val pastEpochDay = today.toEpochDays().toLong() - 5
        val viewModel = historyViewModel(
            briefings = listOf(
                BriefingDay(
                    epochDay = pastEpochDay,
                    figureId = testFigure.id,
                    scriptureReference = "John 3:16",
                    scriptureText = "For God so loved the world",
                ),
            ),
        )

        val state = viewModel.state.value as ReaderHistoryContract.UiState.Ready
        val day = state.listDays.first { it.epochDay == pastEpochDay }
        assertEquals("John 3:16", day.scriptureReference)
        assertEquals("For God so loved the world", day.scriptureText)
    }

    @Test
    fun listDayScriptureIsNullWhenNoBriefingRanThatDay() = runTest(testDispatcher) {
        val assignments = (0..6).associateWith { DayAssignment(testFigure.id, null) }
        val viewModel = historyViewModel(assignments = assignments)

        val state = viewModel.state.value as ReaderHistoryContract.UiState.Ready
        val todayRow = state.listDays.first { it.epochDay == state.todayEpochDay }
        assertEquals(null, todayRow.scriptureReference)
        assertEquals(null, todayRow.scriptureText)
    }

    /**
     * Builds the ViewModel and starts collecting its state. `stateIn(WhileSubscribed)` is cold
     * until a subscriber is present, so an active collector in [backgroundScope] is required for
     * `state.value` to reflect the pipeline output.
     */
    private fun TestScope.historyViewModel(
        assignments: Map<Int, DayAssignment> = emptyMap(),
        briefings: List<BriefingDay> = emptyList(),
        figures: List<Figure> = listOf(testFigure),
    ): ReaderHistoryViewModel {
        val figureRepo = HistoryFakeFigureRepository(figures)
        val dayAssignmentRepo = HistoryFakeDayAssignmentRepository(MutableStateFlow(assignments))
        val quoteRepo = HistoryFakeQuoteRepository()
        val reflectionRepo = HistoryFakeDailyReflectionRepository(briefings)
        val viewModel = ReaderHistoryViewModel(
            getReaderCalendar = GetReaderCalendarUseCase(figureRepo, dayAssignmentRepo, quoteRepo, reflectionRepo),
            reflectionRepository = reflectionRepo,
        )
        backgroundScope.launch(testDispatcher) { viewModel.state.collect {} }
        return viewModel
    }
}

private class HistoryFakeFigureRepository(private val figures: List<Figure>) : FigureRepository {
    private val flow = MutableStateFlow(figures)
    override fun observeAllFigures(): Flow<List<Figure>> = flow
    override fun observeFiguresByCategory(category: FigureCategory): Flow<List<Figure>> = MutableStateFlow(emptyList())
    override suspend fun getFigureById(id: Long): Figure? = figures.firstOrNull { it.id == id }
    override suspend fun getFigureByName(name: String): Figure? = figures.firstOrNull { it.name == name }
    override suspend fun syncFigures() = Unit
}

private class HistoryFakeDayAssignmentRepository(
    private val assignmentsFlow: MutableStateFlow<Map<Int, DayAssignment>>,
) : DayAssignmentRepository {
    override fun observeAssignments(): Flow<Map<Int, DayAssignment>> = assignmentsFlow
    override suspend fun assign(dayOfWeek: Int, figureId: Long, lens: LensFilter?) = Unit
    override suspend fun clear(dayOfWeek: Int) = Unit
    override val isResolved: StateFlow<Boolean> = MutableStateFlow(true)
    override suspend fun resolveReporter(epochDay: Long, dayOfWeek: Int): Long? = null
    override suspend fun resolve(userId: String?) = Unit
}

private class HistoryFakeDailyReflectionRepository(
    private val briefings: List<BriefingDay> = emptyList(),
) : DailyReflectionRepository {
    override suspend fun getOrFetch(
        figureId: Long,
        figureName: String,
        headlines: List<String>,
        tone: String,
        theme: String?,
    ): DailyReflection = throw UnsupportedOperationException()
    override fun observeByEpochDayRange(startEpochDay: Long, endEpochDay: Long): Flow<List<BriefingDay>> =
        MutableStateFlow(briefings.filter { it.epochDay in startEpochDay..endEpochDay })
    override suspend fun getForDay(epochDay: Long, tone: String): DailyReflection? = null
    override suspend fun getEarliestBriefingEpochDay(): Long? = briefings.minOfOrNull { it.epochDay }
    override suspend fun getLockedFigureId(epochDay: Long): Long? = null
}

private class HistoryFakeQuoteRepository(private val latestQuote: Quote? = null) : QuoteRepository {
    override fun observeAllQuotes(): Flow<List<Quote>> = MutableStateFlow(listOfNotNull(latestQuote))
    override fun observeQuotesByFigure(figureId: Long): Flow<List<Quote>> = MutableStateFlow(listOfNotNull(latestQuote))
    override suspend fun getQuoteById(id: Long): Quote? = latestQuote?.takeIf { it.id == id }
    override suspend fun getLatestQuoteForFigure(figureId: Long): Quote? = latestQuote
    override suspend fun saveQuote(text: String, source: String, themes: List<String>, figureId: Long) = Unit
}
