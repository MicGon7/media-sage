@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.mediasage.feature.you

import com.mediasage.data.repository.epochMillis
import com.mediasage.domain.model.BriefingDay
import com.mediasage.domain.model.DailyReflection
import com.mediasage.domain.model.DayAssignment
import com.mediasage.domain.model.Encouragement
import com.mediasage.domain.model.Figure
import com.mediasage.domain.model.FigureCategory
import com.mediasage.domain.model.LensFilter
import com.mediasage.domain.model.Quote
import com.mediasage.domain.repository.DailyReflectionRepository
import com.mediasage.domain.repository.DayAssignmentRepository
import com.mediasage.domain.repository.EncouragementRepository
import com.mediasage.domain.repository.FigureRepository
import com.mediasage.domain.repository.QuoteRepository
import com.mediasage.domain.usecase.GetDayDetailUseCase
import com.mediasage.domain.usecase.GetReaderCalendarUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
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
import kotlin.test.assertNull
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

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun initialStateShowsCurrentMonth() = runTest(testDispatcher) {
        val viewModel = historyViewModel()

        val state = viewModel.state.value as ReaderHistoryContract.UiState.Ready
        val daysInMonth = today.let { LocalDate(it.year, it.monthNumber, 1) }
        assertTrue(state.calendarDays.isNotEmpty())
        assertEquals(1, state.calendarDays.first().dateNumber)
        assertEquals(daysInMonth.monthNumber, LocalDate.fromEpochDays(state.calendarDays.first().epochDay.toInt()).monthNumber)
    }

    @Test
    fun todayShowsCurrentWeeklyAssignment() = runTest(testDispatcher) {
        val assignments = (0..6).associateWith { DayAssignment(testFigure.id, null) }
        val viewModel = historyViewModel(assignments = assignments)

        val state = viewModel.state.value as ReaderHistoryContract.UiState.Ready
        val todayCell = state.calendarDays.first { it.isToday }
        assertTrue(todayCell.hasData)
        assertEquals(testFigure.name, todayCell.figureName)
    }

    @Test
    fun futureDaysNeverShowData() = runTest(testDispatcher) {
        val assignments = (0..6).associateWith { DayAssignment(testFigure.id, null) }
        val viewModel = historyViewModel(assignments = assignments)

        val state = viewModel.state.value as ReaderHistoryContract.UiState.Ready
        val futureDay = state.calendarDays.firstOrNull { it.isFuture } ?: return@runTest
        assertFalse(futureDay.hasData)
        assertEquals(null, futureDay.figureName)
    }

    @Test
    fun pastDayIgnoresWeeklyAssignmentWhenNoBriefingRan() = runTest(testDispatcher) {
        val assignments = (0..6).associateWith { DayAssignment(testFigure.id, null) }
        val viewModel = historyViewModel(assignments = assignments)

        val state = viewModel.state.value as ReaderHistoryContract.UiState.Ready
        val pastDay = state.calendarDays.firstOrNull { !it.isFuture && !it.isToday } ?: return@runTest
        assertFalse(pastDay.hasData)
        assertEquals(null, pastDay.figureName)
    }

    @Test
    fun pastDayShowsBriefingReporterThatActuallyRan() = runTest(testDispatcher) {
        val probe = historyViewModel()
        val pastEpochDay = (probe.state.value as ReaderHistoryContract.UiState.Ready)
            .calendarDays.firstOrNull { !it.isFuture && !it.isToday }?.epochDay ?: return@runTest
        val viewModel = historyViewModel(briefings = listOf(BriefingDay(pastEpochDay, testFigure.id)))

        val state = viewModel.state.value as ReaderHistoryContract.UiState.Ready
        val cell = state.calendarDays.first { it.epochDay == pastEpochDay }
        assertTrue(cell.hasData)
        assertEquals(testFigure.name, cell.figureName)
    }

    @Test
    fun monthPageChangedUpdatesVisibleCalendarDays() = runTest(testDispatcher) {
        val viewModel = historyViewModel()
        val previousMonth = LocalDate(today.year, today.monthNumber, 1)
            .let { kotlinx.datetime.LocalDate.fromEpochDays(it.toEpochDays() - 1) }

        viewModel.onIntent(ReaderHistoryContract.Intent.MonthPageChanged(previousMonth.year, previousMonth.monthNumber))

        val state = viewModel.state.value as ReaderHistoryContract.UiState.Ready
        assertTrue(state.calendarDays.all { LocalDate.fromEpochDays(it.epochDay.toInt()).monthNumber == previousMonth.monthNumber })
    }

    @Test
    fun dayTapped_opensDetailForThatDay() = runTest(testDispatcher) {
        val viewModel = historyViewModel()
        val epochDay = (viewModel.state.value as ReaderHistoryContract.UiState.Ready).todayEpochDay

        viewModel.onIntent(ReaderHistoryContract.Intent.DayTapped(epochDay))

        val state = viewModel.state.value as ReaderHistoryContract.UiState.Ready
        assertEquals(epochDay, state.activeDetail?.epochDay)
    }

    @Test
    fun detailDismissed_clearsActiveDetail() = runTest(testDispatcher) {
        val viewModel = historyViewModel()
        val epochDay = (viewModel.state.value as ReaderHistoryContract.UiState.Ready).todayEpochDay
        viewModel.onIntent(ReaderHistoryContract.Intent.DayTapped(epochDay))

        viewModel.onIntent(ReaderHistoryContract.Intent.DetailDismissed)

        val state = viewModel.state.value as ReaderHistoryContract.UiState.Ready
        assertNull(state.activeDetail)
    }

    /**
     * Builds the ViewModel and starts collecting its state. `stateIn(WhileSubscribed)` is cold
     * until a subscriber is present, so an active collector in [backgroundScope] is required for
     * `state.value` to reflect the pipeline output.
     */
    private fun TestScope.historyViewModel(
        assignments: Map<Int, DayAssignment> = emptyMap(),
        briefings: List<BriefingDay> = emptyList(),
    ): ReaderHistoryViewModel {
        val figureRepo = HistoryFakeFigureRepository(listOf(testFigure))
        val dayAssignmentRepo = HistoryFakeDayAssignmentRepository(MutableStateFlow(assignments))
        val quoteRepo = HistoryFakeQuoteRepository()
        val reflectionRepo = HistoryFakeDailyReflectionRepository(briefings)
        val encouragementRepo = HistoryFakeEncouragementRepository()
        val viewModel = ReaderHistoryViewModel(
            getReaderCalendar = GetReaderCalendarUseCase(figureRepo, dayAssignmentRepo, quoteRepo, reflectionRepo),
            getDayDetail = GetDayDetailUseCase(reflectionRepo, encouragementRepo),
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
    override suspend fun seedDefaultsIfEmpty() = Unit
    override suspend fun resolveReporter(epochDay: Long, dayOfWeek: Int): Long? = null
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
    override suspend fun getForDay(epochDay: Long): DailyReflection? = null
}

private class HistoryFakeQuoteRepository(private val latestQuote: Quote? = null) : QuoteRepository {
    override fun observeAllQuotes(): Flow<List<Quote>> = MutableStateFlow(listOfNotNull(latestQuote))
    override fun observeQuotesByFigure(figureId: Long): Flow<List<Quote>> = MutableStateFlow(listOfNotNull(latestQuote))
    override suspend fun getQuoteById(id: Long): Quote? = latestQuote?.takeIf { it.id == id }
    override suspend fun getLatestQuoteForFigure(figureId: Long): Quote? = latestQuote
    override suspend fun saveQuote(text: String, source: String, themes: List<String>, figureId: Long) = Unit
}

private class HistoryFakeEncouragementRepository : EncouragementRepository {
    override suspend fun getEncouragement(
        headlineTitle: String,
        headlineSource: String,
        headlineImageUrl: String?,
        articleUrl: String?,
        articleSnippet: String?,
    ): Encouragement = throw UnsupportedOperationException()
    override fun observeAll(): Flow<List<Encouragement>> = MutableStateFlow(emptyList())
    override fun observeBookmarked(): Flow<List<Encouragement>> = MutableStateFlow(emptyList())
    override fun observeCountByFigureName(): Flow<Map<String, Int>> = MutableStateFlow(emptyMap())
    override fun observeByFigureId(figureId: Long): Flow<List<Encouragement>> = MutableStateFlow(emptyList())
    override fun observeIsBookmarked(articleUrl: String): Flow<Boolean> = MutableStateFlow(false)
    override suspend fun toggleBookmark(articleUrl: String) = Unit
    override fun observeByEpochDay(epochDay: Long): Flow<List<Encouragement>> = MutableStateFlow(emptyList())
    override fun observeActiveEpochDays(): Flow<Set<Long>> = MutableStateFlow(emptySet())
}
