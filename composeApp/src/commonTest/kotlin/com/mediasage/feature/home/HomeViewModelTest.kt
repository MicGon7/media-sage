@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.mediasage.feature.home

import com.mediasage.domain.model.DailyReflection
import com.mediasage.domain.model.Figure
import com.mediasage.domain.model.FigureCategory
import com.mediasage.domain.model.Headline
import com.mediasage.domain.repository.DailyReflectionRepository
import com.mediasage.domain.repository.FigureRepository
import com.mediasage.domain.repository.HeadlineRepository
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

class HomeViewModelTest {

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
    fun emitsEmptyWhenFetchSucceedsAndDbHasNoHeadlines() = runTest(testDispatcher) {
        val fakeRepo = FakeHeadlineRepository(initialHeadlines = emptyList())
        val viewModel = HomeViewModel(fakeRepo, FakePinnedFigureRepository(), FakeDailyReflectionRepository(), FakeFigureRepository())

        assertIs<HomeContract.UiState.Empty>(viewModel.state.value)
    }

    @Test
    fun emitsSuccessWhenDbHasHeadlines() = runTest(testDispatcher) {
        val headline = Headline(1L, "Breaking News", "Reuters", "https://example.com", null, 0L, 0L)
        val fakeRepo = FakeHeadlineRepository(initialHeadlines = listOf(headline))
        val viewModel = HomeViewModel(fakeRepo, FakePinnedFigureRepository(), FakeDailyReflectionRepository(), FakeFigureRepository())

        assertIs<HomeContract.UiState.Success>(viewModel.state.value)
    }

    @Test
    fun emitsEmptyWhenDbBecomesEmptyAfterHavingContent() = runTest(testDispatcher) {
        val headline = Headline(1L, "Breaking News", "Reuters", "https://example.com", null, 0L, 0L)
        val headlinesFlow = MutableStateFlow(listOf(headline))
        val fakeRepo = FakeHeadlineRepository(headlinesFlow = headlinesFlow)
        val viewModel = HomeViewModel(fakeRepo, FakePinnedFigureRepository(), FakeDailyReflectionRepository(), FakeFigureRepository())

        assertIs<HomeContract.UiState.Success>(viewModel.state.value)

        headlinesFlow.value = emptyList()

        assertIs<HomeContract.UiState.Empty>(viewModel.state.value)
    }

    @Test
    fun refreshFromEmptyStateTriggersRefreshOnRepository() = runTest(testDispatcher) {
        val fakeRepo = FakeHeadlineRepository(initialHeadlines = emptyList())
        val viewModel = HomeViewModel(fakeRepo, FakePinnedFigureRepository(), FakeDailyReflectionRepository(), FakeFigureRepository())

        assertIs<HomeContract.UiState.Empty>(viewModel.state.value)
        val callsAfterInit = fakeRepo.refreshCallCount

        viewModel.onIntent(HomeContract.Intent.RefreshHeadlines)

        assertEquals(callsAfterInit + 1, fakeRepo.refreshCallCount)
    }
}

private class FakePinnedFigureRepository : PinnedFigureRepository {
    override fun observePinnedFigureId(): Flow<Long?> = flowOf(null)
    override suspend fun setPinnedFigureId(figureId: Long?) = Unit
}

private class FakeDailyReflectionRepository : DailyReflectionRepository {
    override suspend fun getOrFetch(figureId: Long, figureName: String, headlines: List<String>, tone: String) =
        DailyReflection("Psalm 46:10", "Be still, and know that I am God.", "A reflection.", emptyList(), tone)
}

private class FakeFigureRepository : FigureRepository {
    override fun observeAllFigures(): Flow<List<Figure>> = flowOf(emptyList())
    override fun observeFiguresByCategory(category: FigureCategory): Flow<List<Figure>> = flowOf(emptyList())
    override suspend fun getFigureById(id: Long): Figure? = null
    override suspend fun getFigureByName(name: String): Figure? = null
    override suspend fun syncFigures() = Unit
}

private class FakeHeadlineRepository(
    initialHeadlines: List<Headline> = emptyList(),
    headlinesFlow: MutableStateFlow<List<Headline>>? = null
) : HeadlineRepository {

    private val _headlines = headlinesFlow ?: MutableStateFlow(initialHeadlines)
    var refreshCallCount = 0

    override fun observeHeadlines(): Flow<List<Headline>> = _headlines

    override suspend fun getHeadlineById(id: Long): Headline? = _headlines.value.find { it.id == id }

    override suspend fun getHeadlineByUrl(url: String): Headline? = _headlines.value.find { it.url == url }

    override suspend fun refreshHeadlines() {
        refreshCallCount++
    }

    override suspend fun clearOldHeadlines(olderThanMillis: Long) = Unit
}
