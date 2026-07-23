@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.mediasage.feature.daydetail

import com.mediasage.domain.model.BriefingDay
import com.mediasage.domain.model.DailyReflection
import com.mediasage.domain.model.Encouragement
import com.mediasage.domain.repository.DailyReflectionRepository
import com.mediasage.domain.repository.EncouragementRepository
import com.mediasage.domain.usecase.GetDayDetailUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DayDetailViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    private fun reflection(tone: String) = DailyReflection(
        scriptureReference = "John 3:16",
        scriptureText = "For God so loved the world",
        insight = "insight-$tone",
        implication = "implication-$tone",
        inspiration = "inspiration-$tone",
        sources = emptyList(),
        tone = tone,
    )

    private fun article(url: String) = Encouragement(
        summary = null,
        quoteText = "Our heart is restless.",
        figureName = "Augustine of Hippo",
        figureRole = "Bishop of Hippo",
        scriptureReference = "John 3:16",
        scriptureText = "For God so loved the world",
        explanation = "explanation",
        connectionThemes = emptyList(),
        matchTheme = "hope",
        tone = "morning",
        headlineTitle = "Headline",
        articleUrl = url,
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
    fun bothReflectionsPresent_showsTwoReflectionSummaries() = runTest(testDispatcher) {
        val viewModel = dayDetailViewModel(
            morning = reflection("morning"),
            evening = reflection("evening"),
        )

        val state = viewModel.state.value as DayDetailContract.UiState.Ready
        assertEquals(2, state.reflections.size)
    }

    @Test
    fun onlyOneReflectionGenerated_showsSingleReflectionGracefully() = runTest(testDispatcher) {
        val viewModel = dayDetailViewModel(morning = reflection("morning"), evening = null)

        val state = viewModel.state.value as DayDetailContract.UiState.Ready
        assertEquals(1, state.reflections.size)
        assertEquals("morning", state.reflections.first().tone)
    }

    @Test
    fun noReflectionsGenerated_reflectionsListIsEmpty() = runTest(testDispatcher) {
        val viewModel = dayDetailViewModel(morning = null, evening = null)

        val state = viewModel.state.value as DayDetailContract.UiState.Ready
        assertTrue(state.reflections.isEmpty())
    }

    @Test
    fun articlesReflectSavedEncouragementsForThatDay() = runTest(testDispatcher) {
        val viewModel = dayDetailViewModel(articles = listOf(article("https://example.com/a")))

        val state = viewModel.state.value as DayDetailContract.UiState.Ready
        assertEquals(1, state.articles.size)
    }

    @Test
    fun tabSelectedUpdatesSelectedTab() = runTest(testDispatcher) {
        val viewModel = dayDetailViewModel()

        viewModel.onIntent(DayDetailContract.Intent.TabSelected(DayDetailContract.Tab.ARTICLES))

        val state = viewModel.state.value as DayDetailContract.UiState.Ready
        assertEquals(DayDetailContract.Tab.ARTICLES, state.selectedTab)
    }

    /**
     * Builds the ViewModel and starts collecting its state. `stateIn(WhileSubscribed)` is cold
     * until a subscriber is present, so an active collector in [backgroundScope] is required for
     * `state.value` to reflect the pipeline output.
     */
    private fun TestScope.dayDetailViewModel(
        morning: DailyReflection? = null,
        evening: DailyReflection? = null,
        articles: List<Encouragement> = emptyList(),
    ): DayDetailViewModel {
        val reflectionRepo = FakeDailyReflectionRepository(morning, evening)
        val encouragementRepo = FakeEncouragementRepository(articles)
        val viewModel = DayDetailViewModel(
            epochDay = 10L,
            figureName = "Augustine of Hippo",
            figureImageUrl = null,
            getDayDetail = GetDayDetailUseCase(reflectionRepo, encouragementRepo),
        )
        backgroundScope.launch(testDispatcher) { viewModel.state.collect {} }
        return viewModel
    }
}

private class FakeDailyReflectionRepository(
    private val morning: DailyReflection?,
    private val evening: DailyReflection?,
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

    override suspend fun getForDay(epochDay: Long, tone: String): DailyReflection? =
        if (tone == "morning") morning else evening
}

private class FakeEncouragementRepository(
    private val articles: List<Encouragement>,
) : EncouragementRepository {
    override suspend fun getEncouragement(
        headlineTitle: String,
        headlineSource: String,
        headlineImageUrl: String?,
        articleUrl: String?,
        articleSnippet: String?,
    ): Encouragement = throw UnsupportedOperationException()

    override fun observeAll(): Flow<List<Encouragement>> = MutableStateFlow(articles)
    override fun observeBookmarked(): Flow<List<Encouragement>> = MutableStateFlow(emptyList())
    override fun observeCountByFigureName(): Flow<Map<String, Int>> = MutableStateFlow(emptyMap())
    override fun observeByFigureId(figureId: Long): Flow<List<Encouragement>> = MutableStateFlow(emptyList())
    override fun observeIsBookmarked(articleUrl: String): Flow<Boolean> = MutableStateFlow(false)
    override suspend fun toggleBookmark(articleUrl: String) = Unit
    override fun observeByEpochDay(epochDay: Long): Flow<List<Encouragement>> = MutableStateFlow(articles)
    override fun observeActiveEpochDays(): Flow<Set<Long>> = MutableStateFlow(emptySet())
}
