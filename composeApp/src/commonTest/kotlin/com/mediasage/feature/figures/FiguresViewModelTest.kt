@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.mediasage.feature.figures

import com.mediasage.data.local.dao.EncouragementDao
import com.mediasage.data.local.entity.EncouragementEntity
import com.mediasage.data.local.entity.VoiceFigureProjection
import com.mediasage.domain.model.Figure
import com.mediasage.domain.model.FigureCategory
import com.mediasage.domain.repository.FigureRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
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
        val dao = FakeEncouragementDao(MutableStateFlow(emptyMap()))
        val vm = FiguresViewModel(figureRepo, dao)

        assertIs<FiguresContract.UiState.Success>(vm.state.value)
    }

    @Test
    fun emitsSuccessWithFiguresFromRepository() = runTest(testDispatcher) {
        val figures = listOf(buildFigure("Augustine", "Bishop of Hippo"))
        val figureRepo = FakeFigureRepository(MutableStateFlow(figures))
        val dao = FakeEncouragementDao(MutableStateFlow(emptyMap()))
        val vm = FiguresViewModel(figureRepo, dao)

        val state = assertIs<FiguresContract.UiState.Success>(vm.state.value)
        assertEquals(1, state.figures.size)
        assertEquals("Augustine", state.figures[0].name)
        assertEquals("Bishop of Hippo", state.figures[0].role)
    }

    @Test
    fun quoteCountIsZeroWhenNoEncouragementsCached() = runTest(testDispatcher) {
        val figures = listOf(buildFigure("Augustine", "Bishop of Hippo"))
        val figureRepo = FakeFigureRepository(MutableStateFlow(figures))
        val dao = FakeEncouragementDao(MutableStateFlow(emptyMap()))
        val vm = FiguresViewModel(figureRepo, dao)

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
        val dao = FakeEncouragementDao(MutableStateFlow(counts))
        val vm = FiguresViewModel(figureRepo, dao)

        val state = assertIs<FiguresContract.UiState.Success>(vm.state.value)
        assertEquals(3, state.figures.first { it.name == "Augustine" }.quoteCount)
        assertEquals(1, state.figures.first { it.name == "C.S. Lewis" }.quoteCount)
    }

    @Test
    fun quoteCountUpdatesReactivelyWhenNewEncouragementCached() = runTest(testDispatcher) {
        val figures = listOf(buildFigure("Augustine", "Bishop of Hippo"))
        val countsFlow = MutableStateFlow(emptyMap<String, Int>())
        val figureRepo = FakeFigureRepository(MutableStateFlow(figures))
        val dao = FakeEncouragementDao(countsFlow)
        val vm = FiguresViewModel(figureRepo, dao)

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

private class FakeFigureRepository(
    private val flow: MutableStateFlow<List<Figure>>
) : FigureRepository {
    override fun getAllFigures(): Flow<List<Figure>> = flow
    override fun getFiguresByCategory(category: FigureCategory): Flow<List<Figure>> = flow
    override suspend fun getFigureById(id: Long): Figure? = flow.value.firstOrNull { it.id == id }
    override suspend fun getFigureByName(name: String): Figure? = flow.value.firstOrNull { it.name == name }
    override suspend fun syncFigures() = Unit
}

private class FakeEncouragementDao(
    private val countsFlow: MutableStateFlow<Map<String, Int>>
) : EncouragementDao {
    override fun countByFigureName(): Flow<Map<String, Int>> = countsFlow
    override suspend fun insert(encouragement: EncouragementEntity) = Unit
    override suspend fun getByArticleUrl(articleUrl: String): EncouragementEntity? = null
    override fun getDistinctFigures(): Flow<List<VoiceFigureProjection>> = MutableStateFlow(emptyList())
    override fun getByFigureName(figureName: String): Flow<List<EncouragementEntity>> = MutableStateFlow(emptyList())
    override fun getByFigureId(figureId: Long): Flow<List<EncouragementEntity>> = MutableStateFlow(emptyList())
    override suspend fun getRecentFigureNames(limit: Int): List<String> = emptyList()
    override fun getAll(): Flow<List<EncouragementEntity>> = MutableStateFlow(emptyList())
    override fun getBookmarked(): Flow<List<EncouragementEntity>> = MutableStateFlow(emptyList())
    override fun observeBookmarkState(articleUrl: String): Flow<Boolean> = MutableStateFlow(false)
    override suspend fun toggleBookmark(articleUrl: String) = Unit
    override suspend fun deleteAll() = Unit
}
