@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.mediasage.feature.figures

import com.mediasage.domain.model.Encouragement
import com.mediasage.domain.model.Figure
import com.mediasage.domain.model.FigureCategory
import com.mediasage.domain.repository.EncouragementRepository
import com.mediasage.domain.repository.FigureRepository
import com.mediasage.domain.repository.PinnedFigureRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
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
        val vm = FiguresViewModel(figureRepo, repo, FakePinnedFigureRepository())

        assertIs<FiguresContract.UiState.Success>(vm.state.value)
    }

    @Test
    fun emitsSuccessWithFiguresFromRepository() = runTest(testDispatcher) {
        val figures = listOf(buildFigure("Augustine", "Bishop of Hippo"))
        val figureRepo = FakeFigureRepository(MutableStateFlow(figures))
        val repo = FakeEncouragementRepository(MutableStateFlow(emptyMap()))
        val vm = FiguresViewModel(figureRepo, repo, FakePinnedFigureRepository())

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
        val vm = FiguresViewModel(figureRepo, repo, FakePinnedFigureRepository())

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
        val vm = FiguresViewModel(figureRepo, repo, FakePinnedFigureRepository())

        val state = assertIs<FiguresContract.UiState.Success>(vm.state.value)
        assertEquals(3, state.figures.first { it.name == "Augustine" }.quoteCount)
        assertEquals(1, state.figures.first { it.name == "C.S. Lewis" }.quoteCount)
    }

    @Test
    fun refreshSetsIsRefreshingTrueThenFalse() = runTest(testDispatcher) {
        val figures = listOf(buildFigure("Augustine", "Bishop of Hippo"))
        val figureRepo = FakeFigureRepository(MutableStateFlow(figures))
        val repo = FakeEncouragementRepository(MutableStateFlow(emptyMap()))
        val vm = FiguresViewModel(figureRepo, repo, FakePinnedFigureRepository())

        vm.onIntent(FiguresContract.Intent.Refresh)

        val state = assertIs<FiguresContract.UiState.Success>(vm.state.value)
        assertEquals(false, state.isRefreshing)
    }

    @Test
    fun refreshCallsSyncFigures() = runTest(testDispatcher) {
        val figures = listOf(buildFigure("Augustine", "Bishop of Hippo"))
        val figureRepo = FakeFigureRepository(MutableStateFlow(figures))
        val repo = FakeEncouragementRepository(MutableStateFlow(emptyMap()))
        val vm = FiguresViewModel(figureRepo, repo, FakePinnedFigureRepository())

        vm.onIntent(FiguresContract.Intent.Refresh)

        assertEquals(1, figureRepo.syncCallCount)
    }

    @Test
    fun quoteCountUpdatesReactivelyWhenNewEncouragementCached() = runTest(testDispatcher) {
        val figures = listOf(buildFigure("Augustine", "Bishop of Hippo"))
        val countsFlow = MutableStateFlow(emptyMap<String, Int>())
        val figureRepo = FakeFigureRepository(MutableStateFlow(figures))
        val repo = FakeEncouragementRepository(countsFlow)
        val vm = FiguresViewModel(figureRepo, repo, FakePinnedFigureRepository())

        assertIs<FiguresContract.UiState.Success>(vm.state.value).let {
            assertEquals(0, it.figures[0].quoteCount)
        }

        countsFlow.value = mapOf("Augustine" to 2)

        assertIs<FiguresContract.UiState.Success>(vm.state.value).let {
            assertEquals(2, it.figures[0].quoteCount)
        }
    }
}

private fun buildFigure(name: String, role: String) = Figure(
    id = 1L,
    name = name,
    category = FigureCategory.THEOLOGIAN,
    century = "4th",
    role = role
)

private class FakePinnedFigureRepository : PinnedFigureRepository {
    override fun observePinnedFigureId(): Flow<Long?> = flowOf(null)
    override suspend fun setPinnedFigureId(figureId: Long?) = Unit
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
    override suspend fun toggleBookmark(articleUrl: String) = Unit
    override suspend fun getEncouragement(
        headlineTitle: String,
        headlineSource: String,
        headlineImageUrl: String?,
        articleUrl: String?,
        articleSnippet: String?
    ): Encouragement = error("not used in test")
}
