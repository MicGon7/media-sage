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
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

class ReaderViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    private val testFigure = Figure(
        id = 1L,
        name = "Augustine of Hippo",
        category = FigureCategory.CHURCH_FATHER,
        century = "4th",
        role = "Bishop of Hippo"
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
    fun quoteCardIsPopulatedWithMostRecentSavedQuote() = runTest(testDispatcher) {
        val savedQuote = Quote(id = 1L, figureId = 1L, text = "Our heart is restless.", source = "Confessions", themes = emptyList())
        val viewModel = readerViewModel(figure = testFigure, latestQuote = savedQuote)

        val state = viewModel.state.value as ReaderContract.UiState.Ready
        assertNotNull(state.quoteCard)
        assertEquals("Our heart is restless.", state.quoteCard?.quoteText)
        assertEquals("Augustine of Hippo", state.quoteCard?.figureName)
    }

    @Test
    fun quoteCardIsNullWhenNoQuotesSaved() = runTest(testDispatcher) {
        val viewModel = readerViewModel(figure = testFigure, latestQuote = null)

        val state = viewModel.state.value as ReaderContract.UiState.Ready
        assertNull(state.quoteCard)
    }

    @Test
    fun daySlotTapped_opensWeekSlotPickerForThatDay() = runTest(testDispatcher) {
        val viewModel = readerViewModel(figure = testFigure, latestQuote = null)

        viewModel.onIntent(ReaderContract.Intent.DaySlotTapped(index = 2))

        val state = viewModel.state.value as ReaderContract.UiState.Ready
        val sheet = state.activeSheet as? ReaderContract.ActiveSheet.WeekSlotPicker
        assertNotNull(sheet)
        assertEquals(2, sheet.dayOfWeek)
    }

    @Test
    fun pickerDismissed_clearsActiveSheet() = runTest(testDispatcher) {
        val viewModel = readerViewModel(figure = testFigure, latestQuote = null)
        viewModel.onIntent(ReaderContract.Intent.DaySlotTapped(index = 0))

        viewModel.onIntent(ReaderContract.Intent.PickerDismissed)

        val state = viewModel.state.value as ReaderContract.UiState.Ready
        assertNull(state.activeSheet)
    }

    @Test
    fun figureAssigned_appliesImmediatelyWhenTodayHasNoBriefingYet() = runTest(testDispatcher) {
        val otherFigure = Figure(id = 2L, name = "C.S. Lewis", category = FigureCategory.THEOLOGIAN, century = "20th", role = "Author")
        val (viewModel, dayAssignmentRepo) = readerViewModelWithRepo(figure = testFigure, extraFigures = listOf(otherFigure))

        viewModel.onIntent(ReaderContract.Intent.FigureAssigned(dayOfWeek = todayOrdinal, figureId = 2L, lens = null))

        assertEquals(listOf(Triple(todayOrdinal, 2L, null as LensFilter?)), dayAssignmentRepo.assignCalls)
        val state = viewModel.state.value as ReaderContract.UiState.Ready
        assertNull(state.pendingReassignment)
    }

    @Test
    fun figureAssigned_promptsConfirmationWhenTodayAlreadyBriefedForADifferentFigure() = runTest(testDispatcher) {
        val otherFigure = Figure(id = 2L, name = "C.S. Lewis", category = FigureCategory.THEOLOGIAN, century = "20th", role = "Author")
        val briefing = BriefingDay(epochDay = todayEpochDay, figureId = 1L, scriptureReference = "John 3:16", scriptureText = "…")
        val (viewModel, dayAssignmentRepo) = readerViewModelWithRepo(
            figure = testFigure,
            extraFigures = listOf(otherFigure),
            briefings = listOf(briefing),
        )

        viewModel.onIntent(ReaderContract.Intent.FigureAssigned(dayOfWeek = todayOrdinal, figureId = 2L, lens = null))

        assertTrue(dayAssignmentRepo.assignCalls.isEmpty())
        val state = viewModel.state.value as ReaderContract.UiState.Ready
        val pending = state.pendingReassignment
        assertNotNull(pending)
        assertEquals("Augustine of Hippo", pending.currentFigureName)
        assertEquals("C.S. Lewis", pending.newFigureName)
    }

    @Test
    fun confirmReassignment_appliesTheWriteAndClearsTheDialog() = runTest(testDispatcher) {
        val otherFigure = Figure(id = 2L, name = "C.S. Lewis", category = FigureCategory.THEOLOGIAN, century = "20th", role = "Author")
        val briefing = BriefingDay(epochDay = todayEpochDay, figureId = 1L, scriptureReference = "John 3:16", scriptureText = "…")
        val (viewModel, dayAssignmentRepo) = readerViewModelWithRepo(
            figure = testFigure,
            extraFigures = listOf(otherFigure),
            briefings = listOf(briefing),
        )
        viewModel.onIntent(ReaderContract.Intent.FigureAssigned(dayOfWeek = todayOrdinal, figureId = 2L, lens = null))

        viewModel.onIntent(ReaderContract.Intent.ConfirmReassignment)

        assertEquals(listOf(Triple(todayOrdinal, 2L, null as LensFilter?)), dayAssignmentRepo.assignCalls)
        val state = viewModel.state.value as ReaderContract.UiState.Ready
        assertNull(state.pendingReassignment)
    }

    @Test
    fun cancelReassignment_leavesAssignmentUnchanged() = runTest(testDispatcher) {
        val otherFigure = Figure(id = 2L, name = "C.S. Lewis", category = FigureCategory.THEOLOGIAN, century = "20th", role = "Author")
        val briefing = BriefingDay(epochDay = todayEpochDay, figureId = 1L, scriptureReference = "John 3:16", scriptureText = "…")
        val (viewModel, dayAssignmentRepo) = readerViewModelWithRepo(
            figure = testFigure,
            extraFigures = listOf(otherFigure),
            briefings = listOf(briefing),
        )
        viewModel.onIntent(ReaderContract.Intent.FigureAssigned(dayOfWeek = todayOrdinal, figureId = 2L, lens = null))

        viewModel.onIntent(ReaderContract.Intent.CancelReassignment)

        assertTrue(dayAssignmentRepo.assignCalls.isEmpty())
        val state = viewModel.state.value as ReaderContract.UiState.Ready
        assertNull(state.pendingReassignment)
    }

    /**
     * Builds the ViewModel and starts collecting its state. `stateIn(WhileSubscribed)` is cold
     * until a subscriber is present, so an active collector in [backgroundScope] is required for
     * `state.value` to reflect the pipeline output.
     */
    private fun TestScope.readerViewModel(
        figure: Figure,
        latestQuote: Quote?,
        extraFigures: List<Figure> = emptyList(),
        assignments: Map<Int, DayAssignment> = emptyMap(),
    ): ReaderViewModel = readerViewModelWithRepo(figure, extraFigures, assignments, emptyList(), latestQuote).first

    private fun TestScope.readerViewModelWithRepo(
        figure: Figure,
        extraFigures: List<Figure> = emptyList(),
        assignments: Map<Int, DayAssignment> = emptyMap(),
        briefings: List<BriefingDay> = emptyList(),
        latestQuote: Quote? = null,
    ): Pair<ReaderViewModel, FakeDayAssignmentRepository> {
        val figureRepo = FakeFigureRepository(listOf(figure) + extraFigures)
        val dayAssignmentRepo = FakeDayAssignmentRepository(MutableStateFlow(assignments))
        val quoteRepo = FakeQuoteRepository(latestQuote)
        val reflectionRepo = FakeDailyReflectionRepository(briefings)
        val viewModel = ReaderViewModel(
            getReaderCalendar = GetReaderCalendarUseCase(figureRepo, dayAssignmentRepo, quoteRepo, reflectionRepo),
            dayAssignmentRepository = dayAssignmentRepo,
        )
        backgroundScope.launch(testDispatcher) { viewModel.state.collect {} }
        return viewModel to dayAssignmentRepo
    }

    private companion object {
        val today = Instant.fromEpochMilliseconds(epochMillis()).toLocalDateTime(TimeZone.currentSystemDefault()).date
        val todayOrdinal = today.dayOfWeek.ordinal
        val todayEpochDay = today.toEpochDays().toLong()
    }
}

private class FakeFigureRepository(private val figures: List<Figure>) : FigureRepository {
    private val flow = MutableStateFlow(figures)
    override fun observeAllFigures(): Flow<List<Figure>> = flow
    override fun observeFiguresByCategory(category: FigureCategory): Flow<List<Figure>> = MutableStateFlow(emptyList())
    override suspend fun getFigureById(id: Long): Figure? = figures.firstOrNull { it.id == id }
    override suspend fun getFigureByName(name: String): Figure? = figures.firstOrNull { it.name == name }
    override suspend fun syncFigures() = Unit
}

private class FakeDayAssignmentRepository(
    private val assignmentsFlow: MutableStateFlow<Map<Int, DayAssignment>>
) : DayAssignmentRepository {
    val assignCalls = mutableListOf<Triple<Int, Long, LensFilter?>>()
    val clearCalls = mutableListOf<Int>()
    override fun observeAssignments(): Flow<Map<Int, DayAssignment>> = assignmentsFlow
    override suspend fun assign(dayOfWeek: Int, figureId: Long, lens: LensFilter?) {
        assignCalls.add(Triple(dayOfWeek, figureId, lens))
    }
    override suspend fun clear(dayOfWeek: Int) {
        clearCalls.add(dayOfWeek)
    }
    override val isResolved: StateFlow<Boolean> = MutableStateFlow(true)
    override suspend fun resolveReporter(epochDay: Long, dayOfWeek: Int): Long? = null
    override suspend fun resolve(userId: String?) = Unit
}

private class FakeDailyReflectionRepository(
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
    override suspend fun getLockedFigureId(epochDay: Long): Long? =
        briefings.firstOrNull { it.epochDay == epochDay }?.figureId
    override val isResolved: StateFlow<Boolean> = MutableStateFlow(true)
    override suspend fun resolve(userId: String?) = Unit
}

private class FakeQuoteRepository(private val latestQuote: Quote?) : QuoteRepository {
    override fun observeAllQuotes(): Flow<List<Quote>> = MutableStateFlow(listOfNotNull(latestQuote))
    override fun observeQuotesByFigure(figureId: Long): Flow<List<Quote>> = MutableStateFlow(listOfNotNull(latestQuote))
    override suspend fun getQuoteById(id: Long): Quote? = latestQuote?.takeIf { it.id == id }
    override suspend fun getLatestQuoteForFigure(figureId: Long): Quote? = latestQuote
    override suspend fun saveQuote(text: String, source: String, themes: List<String>, figureId: Long) = Unit
}
