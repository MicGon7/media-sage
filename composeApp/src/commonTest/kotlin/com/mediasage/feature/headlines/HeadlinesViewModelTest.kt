@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.mediasage.feature.headlines

import com.mediasage.domain.model.Headline
import com.mediasage.domain.repository.HeadlineRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
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

class HeadlinesViewModelTest {

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
    fun staysLoadingWhenFetchSucceedsAndDbHasNoHeadlines() = runTest(testDispatcher) {
        val fakeRepo = FakeHeadlineRepository(initialHeadlines = emptyList())
        val viewModel = HeadlinesViewModel(fakeRepo)

        assertIs<HeadlinesContract.UiState.Loading>(viewModel.state.value)
    }

    @Test
    fun emitsSuccessWhenDbHasHeadlines() = runTest(testDispatcher) {
        val headline = Headline(1L, "Breaking News", "Reuters", "https://example.com", null, 0L, 0L)
        val fakeRepo = FakeHeadlineRepository(initialHeadlines = listOf(headline))
        val viewModel = HeadlinesViewModel(fakeRepo)

        assertIs<HeadlinesContract.UiState.Success>(viewModel.state.value)
    }

    @Test
    fun staysSuccessWhenDbBecomesEmptyAfterHavingContent() = runTest(testDispatcher) {
        val headline = Headline(1L, "Breaking News", "Reuters", "https://example.com", null, 0L, 0L)
        val headlinesFlow = MutableStateFlow(listOf(headline))
        val fakeRepo = FakeHeadlineRepository(headlinesFlow = headlinesFlow)
        val viewModel = HeadlinesViewModel(fakeRepo)

        assertIs<HeadlinesContract.UiState.Success>(viewModel.state.value)

        headlinesFlow.value = emptyList()

        assertIs<HeadlinesContract.UiState.Success>(viewModel.state.value)
    }

    @Test
    fun refreshFromLoadingStateTriggersRefreshOnRepository() = runTest(testDispatcher) {
        val fakeRepo = FakeHeadlineRepository(initialHeadlines = emptyList())
        val viewModel = HeadlinesViewModel(fakeRepo)

        assertIs<HeadlinesContract.UiState.Loading>(viewModel.state.value)
        val callsAfterInit = fakeRepo.refreshCallCount

        viewModel.onIntent(HeadlinesContract.Intent.Refresh)

        assertEquals(callsAfterInit + 1, fakeRepo.refreshCallCount)
    }
}

private class FakeHeadlineRepository(
    initialHeadlines: List<Headline> = emptyList(),
    headlinesFlow: MutableStateFlow<List<Headline>>? = null,
    private val refreshDelayMs: Long = 0L
) : HeadlineRepository {

    private val _headlines = headlinesFlow ?: MutableStateFlow(initialHeadlines)
    var refreshCallCount = 0

    override fun observeHeadlines(): Flow<List<Headline>> = _headlines

    override suspend fun getHeadlineById(id: Long): Headline? = _headlines.value.find { it.id == id }

    override suspend fun getHeadlineByUrl(url: String): Headline? = _headlines.value.find { it.url == url }

    override suspend fun refreshHeadlines() {
        if (refreshDelayMs > 0L) delay(refreshDelayMs)
        refreshCallCount++
    }

    override suspend fun clearOldHeadlines(olderThanMillis: Long) = Unit
}
