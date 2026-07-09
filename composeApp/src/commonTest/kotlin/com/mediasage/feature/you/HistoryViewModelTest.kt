@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.mediasage.feature.you

import com.mediasage.domain.model.BriefingDay
import com.mediasage.domain.model.DailyReflection
import com.mediasage.domain.model.DayAssignment
import com.mediasage.domain.model.Encouragement
import com.mediasage.domain.model.Figure
import com.mediasage.domain.model.FigureCategory
import com.mediasage.domain.model.LensFilter
import com.mediasage.domain.repository.DailyReflectionRepository
import com.mediasage.domain.repository.DayAssignmentRepository
import com.mediasage.domain.repository.EncouragementRepository
import com.mediasage.domain.repository.FigureRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.LocalDate
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class HistoryViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun buildVm(
        initialEpochDay: Long = 0L,
        reflectionRepo: DailyReflectionRepository = FakeHistoryReflectionRepository(),
        encouragementRepo: EncouragementRepository = FakeHistoryEncouragementRepository(),
        figureRepo: FigureRepository = FakeHistoryFigureRepository(),
        assignmentRepo: DayAssignmentRepository = FakeHistoryAssignmentRepository(),
    ) = HistoryViewModel(
        initialEpochDay = initialEpochDay,
        reflectionRepository = reflectionRepo,
        encouragementRepository = encouragementRepo,
        figureRepository = figureRepo,
        dayAssignmentRepository = assignmentRepo,
    )

    @Test
    fun transitionsToReadyWhenRepositoriesEmit() = runTest(testDispatcher) {
        val vm = buildVm()
        assertIs<HistoryContract.UiState.Ready>(vm.state.value)
    }

    @Test
    fun defaultModeIsWeek() = runTest(testDispatcher) {
        val vm = buildVm()
        val state = assertIs<HistoryContract.UiState.Ready>(vm.state.value)
        assertEquals(HistoryContract.CalendarMode.WEEK, state.mode)
    }

    @Test
    fun defaultSelectedDayIsNull() = runTest(testDispatcher) {
        val vm = buildVm()
        val state = assertIs<HistoryContract.UiState.Ready>(vm.state.value)
        assertNull(state.selectedEpochDay)
    }

    @Test
    fun selectModeIntentUpdatesMode() = runTest(testDispatcher) {
        val vm = buildVm()
        vm.onIntent(HistoryContract.Intent.SelectMode(HistoryContract.CalendarMode.MONTH))
        val state = assertIs<HistoryContract.UiState.Ready>(vm.state.value)
        assertEquals(HistoryContract.CalendarMode.MONTH, state.mode)
    }

    @Test
    fun selectTabIntentUpdatesSelectedTab() = runTest(testDispatcher) {
        val vm = buildVm()
        vm.onIntent(HistoryContract.Intent.SelectTab(HistoryContract.DayTab.ARTICLES))
        val state = assertIs<HistoryContract.UiState.Ready>(vm.state.value)
        assertEquals(HistoryContract.DayTab.ARTICLES, state.selectedTab)
    }

    @Test
    fun selectDayIntentSetsSelectedEpochDay() = runTest(testDispatcher) {
        val vm = buildVm()
        val epochDay = LocalDate(2024, 1, 15).toEpochDays().toLong()
        vm.onIntent(HistoryContract.Intent.SelectDay(epochDay))
        val state = assertIs<HistoryContract.UiState.Ready>(vm.state.value)
        assertEquals(epochDay, state.selectedEpochDay)
    }

    @Test
    fun selectDayIntentUpdatesCalendarAnchor() = runTest(testDispatcher) {
        val vm = buildVm()
        val expectedDate = LocalDate(2024, 1, 15)
        vm.onIntent(HistoryContract.Intent.SelectDay(expectedDate.toEpochDays().toLong()))
        val state = assertIs<HistoryContract.UiState.Ready>(vm.state.value)
        assertEquals(expectedDate, state.calendarAnchor)
    }

    @Test
    fun selectDayIntentResetsTabToBriefing() = runTest(testDispatcher) {
        val vm = buildVm()
        vm.onIntent(HistoryContract.Intent.SelectTab(HistoryContract.DayTab.ARTICLES))
        val epochDay = LocalDate(2024, 1, 15).toEpochDays().toLong()
        vm.onIntent(HistoryContract.Intent.SelectDay(epochDay))
        val state = assertIs<HistoryContract.UiState.Ready>(vm.state.value)
        assertEquals(HistoryContract.DayTab.BRIEFING, state.selectedTab)
    }

    @Test
    fun clearSelectionIntentClearsSelectedDay() = runTest(testDispatcher) {
        val vm = buildVm()
        vm.onIntent(HistoryContract.Intent.SelectDay(LocalDate(2024, 1, 15).toEpochDays().toLong()))
        vm.onIntent(HistoryContract.Intent.ClearSelection)
        val state = assertIs<HistoryContract.UiState.Ready>(vm.state.value)
        assertNull(state.selectedEpochDay)
        assertNull(state.dayDetail)
    }

    @Test
    fun repositoryUpdatePreservesCurrentMode() = runTest(testDispatcher) {
        val figureFlow = MutableStateFlow(emptyList<Figure>())
        val vm = buildVm(figureRepo = FakeHistoryFigureRepository(figureFlow))
        vm.onIntent(HistoryContract.Intent.SelectMode(HistoryContract.CalendarMode.YEAR))
        figureFlow.value = listOf(Figure(id = 1, name = "Augustine", category = FigureCategory.THEOLOGIAN, century = "5th"))
        val state = assertIs<HistoryContract.UiState.Ready>(vm.state.value)
        assertEquals(HistoryContract.CalendarMode.YEAR, state.mode)
    }

    @Test
    fun repositoryUpdatePreservesSelectedDay() = runTest(testDispatcher) {
        val figureFlow = MutableStateFlow(emptyList<Figure>())
        val vm = buildVm(figureRepo = FakeHistoryFigureRepository(figureFlow))
        val epochDay = LocalDate(2024, 3, 10).toEpochDays().toLong()
        vm.onIntent(HistoryContract.Intent.SelectDay(epochDay))
        figureFlow.value = listOf(Figure(id = 1, name = "Augustine", category = FigureCategory.THEOLOGIAN, century = "5th"))
        val state = assertIs<HistoryContract.UiState.Ready>(vm.state.value)
        assertEquals(epochDay, state.selectedEpochDay)
    }
}

private class FakeHistoryReflectionRepository : DailyReflectionRepository {
    override suspend fun getOrFetch(
        figureId: Long,
        figureName: String,
        headlines: List<String>,
        tone: String,
        theme: String?,
    ): DailyReflection = throw UnsupportedOperationException()

    override fun observeByEpochDayRange(startEpochDay: Long, endEpochDay: Long): Flow<List<BriefingDay>> =
        MutableStateFlow(emptyList())

    override suspend fun getForDay(epochDay: Long): DailyReflection? = null
}

private class FakeHistoryEncouragementRepository : EncouragementRepository {
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

private class FakeHistoryFigureRepository(
    private val flow: MutableStateFlow<List<Figure>> = MutableStateFlow(emptyList()),
) : FigureRepository {
    override fun observeAllFigures(): Flow<List<Figure>> = flow
    override fun observeFiguresByCategory(category: FigureCategory): Flow<List<Figure>> = MutableStateFlow(emptyList())
    override suspend fun getFigureById(id: Long): Figure? = null
    override suspend fun getFigureByName(name: String): Figure? = null
    override suspend fun syncFigures() = Unit
}

private class FakeHistoryAssignmentRepository : DayAssignmentRepository {
    override fun observeAssignments(): Flow<Map<Int, DayAssignment>> = MutableStateFlow(emptyMap())
    override fun observeOverridesByEpochDayRange(start: Long, end: Long): Flow<Map<Long, Long>> = MutableStateFlow(emptyMap())
    override suspend fun assign(dayOfWeek: Int, figureId: Long, lens: LensFilter?) = Unit
    override suspend fun clear(dayOfWeek: Int) = Unit
    override suspend fun seedDefaultsIfEmpty() = Unit
    override suspend fun setOverride(epochDay: Long, figureId: Long) = Unit
    override suspend fun clearOverride(epochDay: Long) = Unit
    override suspend fun resolveReporter(epochDay: Long, dayOfWeek: Int): Long? = null
}
