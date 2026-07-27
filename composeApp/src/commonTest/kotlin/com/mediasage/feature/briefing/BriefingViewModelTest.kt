@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.mediasage.feature.briefing

import com.mediasage.data.repository.epochMillis
import com.mediasage.domain.model.BriefingDay
import com.mediasage.domain.model.DailyReflection
import com.mediasage.domain.model.DayAssignment
import com.mediasage.domain.model.Figure
import com.mediasage.domain.model.FigureCategory
import com.mediasage.domain.model.Headline
import com.mediasage.domain.model.LensFilter
import com.mediasage.domain.repository.DailyReflectionRepository
import com.mediasage.domain.repository.DayAssignmentRepository
import com.mediasage.domain.repository.FigureRepository
import com.mediasage.domain.repository.HeadlineRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.time.Instant

class BriefingViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    private val judson = Figure(id = 1L, name = "Adoniram Judson", category = FigureCategory.MISSIONARY, century = "19th", role = "Missionary to Burma")
    private val lincoln = Figure(id = 2L, name = "Abraham Lincoln", category = FigureCategory.INTELLECTUAL, century = "19th", role = "President")

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun loadCard_keepsLockedFigureEvenWhenAssignmentPointsToADifferentFigure() = runTest(testDispatcher) {
        // Regression test for the MS-658 bug: confirming a reassignment must never swap today's
        // already-displayed briefing, even though the weekday assignment row now points elsewhere.
        val viewModel = briefingViewModel(
            figures = listOf(judson, lincoln),
            assignments = mapOf(todayOrdinal to DayAssignment(figureId = 2L, lens = null)),
            resolveReporterResult = 1L,
        )

        val state = viewModel.state.value as BriefingContract.UiState.Success
        val card = state.card as BriefingContract.CardState.Ready
        assertEquals(1L, card.figureId)
        assertEquals("Adoniram Judson", card.figureName)
    }

    @Test
    fun loadCard_usesCurrentAssignmentWhenTodayIsNotLocked() = runTest(testDispatcher) {
        val viewModel = briefingViewModel(
            figures = listOf(judson, lincoln),
            assignments = mapOf(todayOrdinal to DayAssignment(figureId = 2L, lens = null)),
            resolveReporterResult = null,
        )

        val state = viewModel.state.value as BriefingContract.UiState.Success
        val card = state.card as BriefingContract.CardState.Ready
        assertEquals(2L, card.figureId)
        assertEquals("Abraham Lincoln", card.figureName)
    }

    @Test
    fun loadCard_fallsBackToFirstFigureWhenNoAssignmentAndNotLocked() = runTest(testDispatcher) {
        val viewModel = briefingViewModel(
            figures = listOf(judson, lincoln),
            assignments = emptyMap(),
            resolveReporterResult = null,
        )

        val state = viewModel.state.value as BriefingContract.UiState.Success
        val card = state.card as BriefingContract.CardState.Ready
        assertEquals(1L, card.figureId)
    }

    @Test
    fun loadCard_rapidAssignmentChangesDuringSyncNeverSurfaceCancellationAsError() = runTest(testDispatcher) {
        // Regression test: on a fresh install, day-assignment sync can upsert several rows in
        // quick succession, re-triggering collectLatest and cancelling the in-flight getOrFetch
        // call each time. That cancellation must never be surfaced as a ShowError side effect,
        // and the final, settled emission must still complete and render a Ready card.
        val assignmentsFlow = MutableStateFlow(mapOf(todayOrdinal to DayAssignment(figureId = 1L, lens = null)))
        val reflectionRepo = FakeDailyReflectionRepository(fetchDelayMs = 50)
        val viewModel = BriefingViewModel(
            dayAssignmentRepository = FakeDayAssignmentRepository(assignmentsFlow, resolveReporterResult = null),
            dailyReflectionRepository = reflectionRepo,
            figureRepository = FakeFigureRepository(listOf(judson, lincoln)),
            headlineRepository = FakeHeadlineRepository(),
        )
        val sideEffects = mutableListOf<BriefingContract.SideEffect>()
        backgroundScope.launch(testDispatcher) { viewModel.sideEffects.collect { sideEffects.add(it) } }
        backgroundScope.launch(testDispatcher) { viewModel.state.collect {} }

        repeat(5) {
            advanceTimeBy(1)
            assignmentsFlow.value = mapOf(todayOrdinal to DayAssignment(figureId = 1L, lens = null))
        }
        advanceUntilIdle()

        assertEquals(emptyList(), sideEffects.filterIsInstance<BriefingContract.SideEffect.ShowError>())
        val state = viewModel.state.value as BriefingContract.UiState.Success
        assertIs<BriefingContract.CardState.Ready>(state.card)
    }

    private fun TestScope.briefingViewModel(
        figures: List<Figure>,
        assignments: Map<Int, DayAssignment>,
        resolveReporterResult: Long?,
    ): BriefingViewModel {
        val viewModel = BriefingViewModel(
            dayAssignmentRepository = FakeDayAssignmentRepository(MutableStateFlow(assignments), resolveReporterResult),
            dailyReflectionRepository = FakeDailyReflectionRepository(),
            figureRepository = FakeFigureRepository(figures),
            headlineRepository = FakeHeadlineRepository(),
        )
        backgroundScope.launch(testDispatcher) { viewModel.state.collect {} }
        return viewModel
    }

    private companion object {
        val todayOrdinal = Instant.fromEpochMilliseconds(epochMillis())
            .toLocalDateTime(TimeZone.currentSystemDefault()).date.dayOfWeek.ordinal
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
    private val assignmentsFlow: MutableStateFlow<Map<Int, DayAssignment>>,
    private val resolveReporterResult: Long?,
) : DayAssignmentRepository {
    override fun observeAssignments(): Flow<Map<Int, DayAssignment>> = assignmentsFlow
    override suspend fun assign(dayOfWeek: Int, figureId: Long, lens: LensFilter?) = Unit
    override suspend fun clear(dayOfWeek: Int) = Unit
    override suspend fun seedDefaultsIfEmpty() = Unit
    override suspend fun resolveReporter(epochDay: Long, dayOfWeek: Int): Long? =
        resolveReporterResult ?: assignmentsFlow.value[dayOfWeek]?.figureId
    override suspend fun syncWithRemote(userId: String) = Unit
}

private class FakeDailyReflectionRepository(private val fetchDelayMs: Long = 0) : DailyReflectionRepository {
    override suspend fun getOrFetch(
        figureId: Long,
        figureName: String,
        headlines: List<String>,
        tone: String,
        theme: String?,
    ): DailyReflection {
        if (fetchDelayMs > 0) delay(fetchDelayMs)
        return DailyReflection(
            scriptureReference = "John 3:16",
            scriptureText = "For God so loved the world",
            insight = "insight",
            implication = "implication",
            inspiration = "inspiration",
            sources = emptyList(),
            tone = tone,
            theme = theme,
        )
    }
    override fun observeByEpochDayRange(startEpochDay: Long, endEpochDay: Long): Flow<List<BriefingDay>> =
        MutableStateFlow(emptyList())
    override suspend fun getForDay(epochDay: Long, tone: String): DailyReflection? = null
    override suspend fun getEarliestBriefingEpochDay(): Long? = null
    override suspend fun getLockedFigureId(epochDay: Long): Long? = null
}

private class FakeHeadlineRepository : HeadlineRepository {
    override fun observeHeadlines(): Flow<List<Headline>> = MutableStateFlow(emptyList())
    override suspend fun getHeadlineById(id: Long): Headline? = null
    override suspend fun getHeadlineByUrl(url: String): Headline? = null
    override suspend fun refreshHeadlines() = Unit
    override suspend fun clearOldHeadlines(olderThanMillis: Long) = Unit
}
