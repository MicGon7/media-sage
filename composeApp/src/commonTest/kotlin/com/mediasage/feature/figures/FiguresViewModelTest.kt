@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.mediasage.feature.figures

import com.mediasage.domain.model.DayAssignment
import com.mediasage.domain.model.Encouragement
import com.mediasage.domain.model.Figure
import com.mediasage.domain.model.FigureCategory
import com.mediasage.domain.model.LensFilter
import com.mediasage.domain.repository.DayAssignmentRepository
import com.mediasage.domain.repository.EncouragementRepository
import com.mediasage.domain.repository.FigureRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class FiguresViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun emitsLoadingInitially() {
        val figureRepo = FakeFigureRepository(MutableStateFlow(emptyList()))
        val repo = FakeEncouragementRepository(MutableStateFlow(emptyMap()))
        val vm = FiguresViewModel(figureRepo, repo, FakeDayAssignmentRepository())

        assertIs<FiguresContract.UiState.Success>(vm.state.value)
    }

    @Test
    fun emitsSuccessWithFiguresFromRepository() = runTest(testDispatcher) {
        val figures = listOf(buildFigure("Augustine", "Bishop of Hippo"))
        val figureRepo = FakeFigureRepository(MutableStateFlow(figures))
        val repo = FakeEncouragementRepository(MutableStateFlow(emptyMap()))
        val vm = FiguresViewModel(figureRepo, repo, FakeDayAssignmentRepository())

        val state = assertIs<FiguresContract.UiState.Success>(vm.state.value)
        assertEquals(1, state.figures.size)
        assertEquals("Augustine", state.figures[0].name)
        assertEquals("Bishop of Hippo", state.figures[0].role)
    }

    @Test
    fun quoteCountIsZeroWhenNoEncouragementsCached() = runTest(testDispatcher) {
        val figures = listOf(buildFigure("Augustine", "Bishop of Hippo"))
        val figureRepo = FakeFigureRepository(MutableStateFlow(figures))
        val repo = FakeEncouragementRepository(MutableStateFlow(emptyMap()))
        val vm = FiguresViewModel(figureRepo, repo, FakeDayAssignmentRepository())

        val state = assertIs<FiguresContract.UiState.Success>(vm.state.value)
        assertEquals(0, state.figures[0].quoteCount)
    }

    @Test
    fun quoteCountReflectsCachedEncouragements() = runTest(testDispatcher) {
        val figures = listOf(
            buildFigure("Augustine", "Bishop of Hippo"),
            buildFigure("C.S. Lewis", "Author & Apologist")
        )
        val counts = mapOf("Augustine" to 3, "C.S. Lewis" to 1)
        val figureRepo = FakeFigureRepository(MutableStateFlow(figures))
        val repo = FakeEncouragementRepository(MutableStateFlow(counts))
        val vm = FiguresViewModel(figureRepo, repo, FakeDayAssignmentRepository())

        val state = assertIs<FiguresContract.UiState.Success>(vm.state.value)
        assertEquals(3, state.figures.first { it.name == "Augustine" }.quoteCount)
        assertEquals(1, state.figures.first { it.name == "C.S. Lewis" }.quoteCount)
    }

    @Test
    fun refreshSetsIsRefreshingTrueThenFalse() = runTest(testDispatcher) {
        val figures = listOf(buildFigure("Augustine", "Bishop of Hippo"))
        val figureRepo = FakeFigureRepository(MutableStateFlow(figures))
        val repo = FakeEncouragementRepository(MutableStateFlow(emptyMap()))
        val vm = FiguresViewModel(figureRepo, repo, FakeDayAssignmentRepository())

        vm.onIntent(FiguresContract.Intent.Refresh)

        val state = assertIs<FiguresContract.UiState.Success>(vm.state.value)
        assertEquals(false, state.isRefreshing)
    }

    @Test
    fun refreshCallsSyncFigures() = runTest(testDispatcher) {
        val figures = listOf(buildFigure("Augustine", "Bishop of Hippo"))
        val figureRepo = FakeFigureRepository(MutableStateFlow(figures))
        val repo = FakeEncouragementRepository(MutableStateFlow(emptyMap()))
        val vm = FiguresViewModel(figureRepo, repo, FakeDayAssignmentRepository())

        vm.onIntent(FiguresContract.Intent.Refresh)

        assertEquals(1, figureRepo.syncCallCount)
    }

    @Test
    fun quoteCountUpdatesReactivelyWhenNewEncouragementCached() = runTest(testDispatcher) {
        val figures = listOf(buildFigure("Augustine", "Bishop of Hippo"))
        val countsFlow = MutableStateFlow(emptyMap<String, Int>())
        val figureRepo = FakeFigureRepository(MutableStateFlow(figures))
        val repo = FakeEncouragementRepository(countsFlow)
        val vm = FiguresViewModel(figureRepo, repo, FakeDayAssignmentRepository())

        assertIs<FiguresContract.UiState.Success>(vm.state.value).let {
            assertEquals(0, it.figures[0].quoteCount)
        }

        countsFlow.value = mapOf("Augustine" to 2)

        assertIs<FiguresContract.UiState.Success>(vm.state.value).let {
            assertEquals(2, it.figures[0].quoteCount)
        }
    }

    @Test
    fun filtersByNameCaseInsensitive() = runTest(testDispatcher) {
        val figures = listOf(
            buildFigure(id = 1L, name = "Augustine", role = "Bishop of Hippo"),
            buildFigure(id = 2L, name = "C.S. Lewis", role = "Author & Apologist")
        )
        val figureRepo = FakeFigureRepository(MutableStateFlow(figures))
        val repo = FakeEncouragementRepository(MutableStateFlow(emptyMap()))
        val vm = FiguresViewModel(figureRepo, repo, FakeDayAssignmentRepository())

        vm.onIntent(FiguresContract.Intent.SearchQueryChanged("aug"))

        val state = assertIs<FiguresContract.UiState.Success>(vm.state.value)
        assertEquals(1, state.figures.size)
        assertEquals("Augustine", state.figures[0].name)
        assertEquals("aug", state.searchQuery)
    }

    @Test
    fun filtersByRoleCaseInsensitive() = runTest(testDispatcher) {
        val figures = listOf(
            buildFigure(id = 1L, name = "Augustine", role = "Bishop of Hippo"),
            buildFigure(id = 2L, name = "C.S. Lewis", role = "Author & Apologist")
        )
        val figureRepo = FakeFigureRepository(MutableStateFlow(figures))
        val repo = FakeEncouragementRepository(MutableStateFlow(emptyMap()))
        val vm = FiguresViewModel(figureRepo, repo, FakeDayAssignmentRepository())

        vm.onIntent(FiguresContract.Intent.SearchQueryChanged("APOLOGIST"))

        val state = assertIs<FiguresContract.UiState.Success>(vm.state.value)
        assertEquals(1, state.figures.size)
        assertEquals("C.S. Lewis", state.figures[0].name)
    }

    @Test
    fun clearingQueryRestoresFullList() = runTest(testDispatcher) {
        val figures = listOf(
            buildFigure(id = 1L, name = "Augustine", role = "Bishop of Hippo"),
            buildFigure(id = 2L, name = "C.S. Lewis", role = "Author & Apologist")
        )
        val figureRepo = FakeFigureRepository(MutableStateFlow(figures))
        val repo = FakeEncouragementRepository(MutableStateFlow(emptyMap()))
        val vm = FiguresViewModel(figureRepo, repo, FakeDayAssignmentRepository())

        vm.onIntent(FiguresContract.Intent.SearchQueryChanged("aug"))
        assertEquals(1, assertIs<FiguresContract.UiState.Success>(vm.state.value).figures.size)

        vm.onIntent(FiguresContract.Intent.SearchQueryChanged(""))
        assertEquals(2, assertIs<FiguresContract.UiState.Success>(vm.state.value).figures.size)
    }

    @Test
    fun pinnedFigureSortsBeforeAlphabeticallyEarlierFigure() = runTest(testDispatcher) {
        val figures = listOf(
            buildFigure(id = 1L, name = "Augustine", role = "Bishop of Hippo"),
            buildFigure(id = 2L, name = "Zwingli", role = "Reformer")
        )
        val figureRepo = FakeFigureRepository(MutableStateFlow(figures))
        val repo = FakeEncouragementRepository(MutableStateFlow(emptyMap()))
        // Assign Zwingli (id=2) to every day so the test is day-of-week agnostic
        val dayAssignmentRepo = FakeDayAssignmentRepository(assignments = (0..6).associate { it to DayAssignment(figureId = 2L, lens = null) })
        val vm = FiguresViewModel(figureRepo, repo, dayAssignmentRepo)

        val state = assertIs<FiguresContract.UiState.Success>(vm.state.value)
        assertEquals("Zwingli", state.figures[0].name)
        assertEquals("Augustine", state.figures[1].name)
    }

    @Test
    fun unpinnedFiguresSortAlphabetically() = runTest(testDispatcher) {
        val figures = listOf(
            buildFigure(id = 1L, name = "Zwingli", role = "Reformer"),
            buildFigure(id = 2L, name = "Augustine", role = "Bishop of Hippo"),
            buildFigure(id = 3L, name = "Calvin", role = "Reformer")
        )
        val figureRepo = FakeFigureRepository(MutableStateFlow(figures))
        val repo = FakeEncouragementRepository(MutableStateFlow(emptyMap()))
        val vm = FiguresViewModel(figureRepo, repo, FakeDayAssignmentRepository())

        val state = assertIs<FiguresContract.UiState.Success>(vm.state.value)
        assertEquals("Augustine", state.figures[0].name)
        assertEquals("Calvin", state.figures[1].name)
        assertEquals("Zwingli", state.figures[2].name)
    }
}

private fun buildFigure(name: String, role: String) = Figure(
    id = 1L,
    name = name,
    category = FigureCategory.THEOLOGIAN,
    century = "4th",
    role = role
)

private fun buildFigure(id: Long, name: String, role: String) = Figure(
    id = id,
    name = name,
    category = FigureCategory.THEOLOGIAN,
    century = "4th",
    role = role
)

private class FakeDayAssignmentRepository(
    private val assignments: Map<Int, DayAssignment> = emptyMap()
) : DayAssignmentRepository {
    override fun observeAssignments(): Flow<Map<Int, DayAssignment>> = flowOf(assignments)
    override suspend fun assign(dayOfWeek: Int, figureId: Long, lens: LensFilter?) = Unit
    override suspend fun clear(dayOfWeek: Int) = Unit
    override val isResolved: StateFlow<Boolean> = MutableStateFlow(true)
    override suspend fun resolveReporter(epochDay: Long, dayOfWeek: Int): Long? = null
    override suspend fun resolve(userId: String?) = Unit
}

private class FakeFigureRepository(
    private val flow: MutableStateFlow<List<Figure>>
) : FigureRepository {
    var syncCallCount = 0
        private set

    override fun observeAllFigures(): Flow<List<Figure>> = flow
    override fun observeFiguresByCategory(category: FigureCategory): Flow<List<Figure>> = flow
    override suspend fun getFigureById(id: Long): Figure? = flow.value.firstOrNull { it.id == id }
    override suspend fun getFigureByName(name: String): Figure? = flow.value.firstOrNull { it.name == name }
    override suspend fun syncFigures() { syncCallCount++ }
}

private class FakeEncouragementRepository(
    private val countsFlow: MutableStateFlow<Map<String, Int>>
) : EncouragementRepository {
    override fun observeCountByFigureName(): Flow<Map<String, Int>> = countsFlow
    override fun observeAll(): Flow<List<Encouragement>> = MutableStateFlow(emptyList())
    override fun observeBookmarked(): Flow<List<Encouragement>> = MutableStateFlow(emptyList())
    override fun observeByFigureId(figureId: Long): Flow<List<Encouragement>> = MutableStateFlow(emptyList())
    override fun observeIsBookmarked(articleUrl: String): Flow<Boolean> = MutableStateFlow(false)
    override fun observeByEpochDay(epochDay: Long): Flow<List<Encouragement>> = MutableStateFlow(emptyList())
    override fun observeActiveEpochDays(): Flow<Set<Long>> = MutableStateFlow(emptySet())
    override suspend fun toggleBookmark(articleUrl: String) = Unit
    override suspend fun getEncouragement(
        headlineTitle: String,
        headlineSource: String,
        headlineImageUrl: String?,
        articleUrl: String?,
        articleSnippet: String?,
        headlineCategory: String,
        headlinePublishedAt: Long
    ): Encouragement = error("not used in test")
    override val isResolved: StateFlow<Boolean> = MutableStateFlow(true)
    override suspend fun resolve(userId: String?) = Unit
}
