@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.mediasage.feature.figures

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

class FigureDetailViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    private val augustine = Figure(
        id = 1L, name = "Augustine of Hippo", category = FigureCategory.CHURCH_FATHER, century = "4th", role = "Bishop of Hippo"
    )
    private val lewis = Figure(id = 2L, name = "C.S. Lewis", category = FigureCategory.THEOLOGIAN, century = "20th", role = "Author")

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun pinToHome_assignsImmediatelyWhenTodayHasNoBriefingYet() = runTest(testDispatcher) {
        val (viewModel, dayAssignmentRepo) = figureDetailViewModel(figureId = 2L, figures = listOf(augustine, lewis))

        viewModel.onIntent(FigureDetailContract.Intent.PinToHome)

        assertEquals(listOf(Triple(todayOrdinal, 2L, null as LensFilter?)), dayAssignmentRepo.assignCalls)
        val state = viewModel.state.value as FigureDetailContract.UiState.Success
        assertNull(state.pendingReassignment)
    }

    @Test
    fun pinToHome_promptsConfirmationWhenTodayAlreadyBriefedForADifferentFigure() = runTest(testDispatcher) {
        val (viewModel, dayAssignmentRepo) = figureDetailViewModel(
            figureId = 2L,
            figures = listOf(augustine, lewis),
            lockedFigureIdsByEpochDay = mapOf(todayEpochDay to 1L),
        )

        viewModel.onIntent(FigureDetailContract.Intent.PinToHome)

        assertTrue(dayAssignmentRepo.assignCalls.isEmpty())
        val state = viewModel.state.value as FigureDetailContract.UiState.Success
        val pending = state.pendingReassignment
        assertNotNull(pending)
        assertEquals("Augustine of Hippo", pending.currentFigureName)
        assertEquals("C.S. Lewis", pending.newFigureName)
    }

    @Test
    fun confirmReassignment_appliesTheAssignmentAndClearsTheDialog() = runTest(testDispatcher) {
        val (viewModel, dayAssignmentRepo) = figureDetailViewModel(
            figureId = 2L,
            figures = listOf(augustine, lewis),
            lockedFigureIdsByEpochDay = mapOf(todayEpochDay to 1L),
        )
        viewModel.onIntent(FigureDetailContract.Intent.PinToHome)

        viewModel.onIntent(FigureDetailContract.Intent.ConfirmReassignment)

        assertEquals(listOf(Triple(todayOrdinal, 2L, null as LensFilter?)), dayAssignmentRepo.assignCalls)
        val state = viewModel.state.value as FigureDetailContract.UiState.Success
        assertNull(state.pendingReassignment)
    }

    @Test
    fun cancelReassignment_leavesAssignmentUnchanged() = runTest(testDispatcher) {
        val (viewModel, dayAssignmentRepo) = figureDetailViewModel(
            figureId = 2L,
            figures = listOf(augustine, lewis),
            lockedFigureIdsByEpochDay = mapOf(todayEpochDay to 1L),
        )
        viewModel.onIntent(FigureDetailContract.Intent.PinToHome)

        viewModel.onIntent(FigureDetailContract.Intent.CancelReassignment)

        assertTrue(dayAssignmentRepo.assignCalls.isEmpty())
        val state = viewModel.state.value as FigureDetailContract.UiState.Success
        assertNull(state.pendingReassignment)
    }

    @Test
    fun pinToHome_unpinningAlreadyPinnedFigureClearsWithNoDialog() = runTest(testDispatcher) {
        val (viewModel, dayAssignmentRepo) = figureDetailViewModel(
            figureId = 1L,
            figures = listOf(augustine, lewis),
            assignments = mapOf(todayOrdinal to DayAssignment(figureId = 1L, lens = null)),
            lockedFigureIdsByEpochDay = mapOf(todayEpochDay to 1L),
        )

        viewModel.onIntent(FigureDetailContract.Intent.PinToHome)

        assertEquals(listOf(todayOrdinal), dayAssignmentRepo.clearCalls)
        val state = viewModel.state.value as FigureDetailContract.UiState.Success
        assertNull(state.pendingReassignment)
    }

    @Test
    fun pinQuote_memorizesTheQuoteForThisFigure() = runTest(testDispatcher) {
        val quoteRepo = DetailFakeQuoteRepository()
        val (viewModel, _) = figureDetailViewModel(
            figureId = 2L,
            figures = listOf(augustine, lewis),
            quoteRepo = quoteRepo,
        )

        viewModel.onIntent(FigureDetailContract.Intent.PinQuote("You are never too old to dream."))

        assertEquals(listOf(2L to "You are never too old to dream."), quoteRepo.memorizeCalls)
    }

    @Test
    fun quotes_reflectWhicheverQuoteIsCurrentlyMemorized() = runTest(testDispatcher) {
        val encouragement = Encouragement(
            summary = null, quoteText = "You are never too old to dream.", figureName = "C.S. Lewis",
            figureRole = "Author", scriptureReference = "", scriptureText = "", explanation = "",
            connectionThemes = emptyList(), matchTheme = "", tone = "", headlineTitle = "Some headline",
        )
        val memorized = Quote(id = 1L, figureId = 2L, text = "You are never too old to dream.", source = "", themes = emptyList())
        val (viewModel, _) = figureDetailViewModel(
            figureId = 2L,
            figures = listOf(augustine, lewis),
            encouragements = listOf(encouragement),
            quoteRepo = DetailFakeQuoteRepository(memorized),
        )

        val state = viewModel.state.value as FigureDetailContract.UiState.Success
        assertTrue(state.quotes.single().isPinned)
    }

    /**
     * Builds the ViewModel and starts collecting its state. `_state` is a plain `MutableStateFlow`
     * fed by a `viewModelScope.launch` in `init { load() }`, which `UnconfinedTestDispatcher` runs
     * eagerly — no separate collector is required for `state.value` to reflect the pipeline output.
     */
    private fun TestScope.figureDetailViewModel(
        figureId: Long,
        figures: List<Figure>,
        assignments: Map<Int, DayAssignment> = emptyMap(),
        lockedFigureIdsByEpochDay: Map<Long, Long> = emptyMap(),
        encouragements: List<Encouragement> = emptyList(),
        quoteRepo: DetailFakeQuoteRepository = DetailFakeQuoteRepository(),
    ): Pair<FigureDetailViewModel, DetailFakeDayAssignmentRepository> {
        val figureRepo = DetailFakeFigureRepository(figures)
        val encouragementRepo = DetailFakeEncouragementRepository(encouragements)
        val dayAssignmentRepo = DetailFakeDayAssignmentRepository(MutableStateFlow(assignments))
        val reflectionRepo = FakeDailyReflectionRepository(lockedFigureIdsByEpochDay)
        val viewModel = FigureDetailViewModel(figureId, figureRepo, encouragementRepo, dayAssignmentRepo, reflectionRepo, quoteRepo)
        backgroundScope.launch(testDispatcher) { viewModel.state.collect {} }
        return viewModel to dayAssignmentRepo
    }

    private companion object {
        val today = Instant.fromEpochMilliseconds(epochMillis()).toLocalDateTime(TimeZone.currentSystemDefault()).date
        val todayOrdinal = today.dayOfWeek.ordinal
        val todayEpochDay = today.toEpochDays().toLong()
    }
}

private class DetailFakeFigureRepository(private val figures: List<Figure>) : FigureRepository {
    private val flow = MutableStateFlow(figures)
    override fun observeAllFigures(): Flow<List<Figure>> = flow
    override fun observeFiguresByCategory(category: FigureCategory): Flow<List<Figure>> = MutableStateFlow(emptyList())
    override suspend fun getFigureById(id: Long): Figure? = figures.firstOrNull { it.id == id }
    override suspend fun getFigureByName(name: String): Figure? = figures.firstOrNull { it.name == name }
    override suspend fun syncFigures() = Unit
}

private class DetailFakeEncouragementRepository(
    private val encouragements: List<Encouragement> = emptyList(),
) : EncouragementRepository {
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
    override fun observeByFigureId(figureId: Long): Flow<List<Encouragement>> = MutableStateFlow(encouragements)
    override fun observeIsBookmarked(articleUrl: String): Flow<Boolean> = MutableStateFlow(false)
    override suspend fun toggleBookmark(articleUrl: String) = Unit
    override fun observeByEpochDay(epochDay: Long): Flow<List<Encouragement>> = MutableStateFlow(emptyList())
    override fun observeActiveEpochDays(): Flow<Set<Long>> = MutableStateFlow(emptySet())
    override val isResolved: StateFlow<Boolean> = MutableStateFlow(true)
    override suspend fun resolve(userId: String?) = Unit
}

private class DetailFakeDayAssignmentRepository(
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

private class DetailFakeQuoteRepository(private val memorizedQuote: Quote? = null) : QuoteRepository {
    val memorizeCalls = mutableListOf<Pair<Long, String>>()
    override fun observeAllQuotes(): Flow<List<Quote>> = MutableStateFlow(listOfNotNull(memorizedQuote))
    override fun observeQuotesByFigure(figureId: Long): Flow<List<Quote>> = MutableStateFlow(listOfNotNull(memorizedQuote))
    override suspend fun getQuoteById(id: Long): Quote? = memorizedQuote?.takeIf { it.id == id }
    override suspend fun getLatestQuoteForFigure(figureId: Long): Quote? = memorizedQuote
    override suspend fun saveQuote(text: String, source: String, themes: List<String>, figureId: Long) = Unit
    override fun observeMemorizedQuote(): Flow<Quote?> = MutableStateFlow(memorizedQuote)
    override suspend fun memorizeQuote(figureId: Long, text: String) {
        memorizeCalls.add(figureId to text)
    }
    override val isResolved: StateFlow<Boolean> = MutableStateFlow(true)
    override suspend fun resolve(userId: String?) = Unit
}

private class FakeDailyReflectionRepository(
    private val lockedFigureIdsByEpochDay: Map<Long, Long> = emptyMap(),
) : DailyReflectionRepository {
    override suspend fun getOrFetch(
        figureId: Long,
        figureName: String,
        headlines: List<String>,
        tone: String,
        theme: String?,
    ): DailyReflection = throw UnsupportedOperationException()
    override fun observeByEpochDayRange(startEpochDay: Long, endEpochDay: Long): Flow<List<BriefingDay>> =
        MutableStateFlow(emptyList())
    override suspend fun getForDay(epochDay: Long, tone: String): DailyReflection? = null
    override suspend fun getEarliestBriefingEpochDay(): Long? = null
    override suspend fun getLockedFigureId(epochDay: Long): Long? = lockedFigureIdsByEpochDay[epochDay]
    override val isResolved: StateFlow<Boolean> = MutableStateFlow(true)
    override suspend fun resolve(userId: String?) = Unit
}
