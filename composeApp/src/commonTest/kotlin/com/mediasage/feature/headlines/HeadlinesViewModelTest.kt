@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.mediasage.feature.headlines

import com.mediasage.data.HeadlineCategoryPreferencesRepository
import com.mediasage.domain.model.Encouragement
import com.mediasage.domain.model.Headline
import com.mediasage.domain.repository.EncouragementRepository
import com.mediasage.domain.repository.HeadlineRepository
import com.mediasage.domain.usecase.GetHeadlinesFeedUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

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
        val viewModel = buildViewModel(initialHeadlines = emptyList())

        assertIs<HeadlinesContract.UiState.Loading>(viewModel.state.value)
    }

    @Test
    fun emitsSuccessWhenDbHasHeadlines() = runTest(testDispatcher) {
        val headline = Headline(1L, "Breaking News", "Reuters", "https://example.com", null, 0L, 0L)
        val viewModel = buildViewModel(initialHeadlines = listOf(headline))

        assertIs<HeadlinesContract.UiState.Success>(viewModel.state.value)
    }

    @Test
    fun staysSuccessWhenDbBecomesEmptyAfterHavingContent() = runTest(testDispatcher) {
        val headline = Headline(1L, "Breaking News", "Reuters", "https://example.com", null, 0L, 0L)
        val headlinesFlow = MutableStateFlow(listOf(headline))
        val viewModel = buildViewModel(headlinesFlow = headlinesFlow)

        assertIs<HeadlinesContract.UiState.Success>(viewModel.state.value)

        headlinesFlow.value = emptyList()

        assertIs<HeadlinesContract.UiState.Success>(viewModel.state.value)
    }

    @Test
    fun refreshFromLoadingStateTriggersRefreshOnRepository() = runTest(testDispatcher) {
        val fakeRepo = FakeHeadlineRepository(initialHeadlines = emptyList())
        val viewModel = buildViewModel(headlineRepository = fakeRepo)

        assertIs<HeadlinesContract.UiState.Loading>(viewModel.state.value)
        val callsAfterInit = fakeRepo.refreshCallCount

        viewModel.onIntent(HeadlinesContract.Intent.Refresh)

        assertEquals(callsAfterInit + 1, fakeRepo.refreshCallCount)
    }

    @Test
    fun defaultSelectionShowsAllHeadlinesUnfiltered() = runTest(testDispatcher) {
        val world = Headline(1L, "World News", "Reuters", "https://example.com/1", null, 0L, 0L, category = "world")
        val business = Headline(2L, "Business News", "AP", "https://example.com/2", null, 0L, 0L, category = "business")
        val viewModel = buildViewModel(initialHeadlines = listOf(world, business))

        val state = viewModel.state.value as HeadlinesContract.UiState.Success
        assertEquals(2, state.headlines.size)
        assertTrue(state.selectedCategories.isEmpty())
    }

    @Test
    fun togglingCategoryFiltersHeadlinesByUnion() = runTest(testDispatcher) {
        val world = Headline(1L, "World News", "Reuters", "https://example.com/1", null, 0L, 0L, category = "world")
        val business = Headline(2L, "Business News", "AP", "https://example.com/2", null, 0L, 0L, category = "business")
        val health = Headline(3L, "Health News", "AP", "https://example.com/3", null, 0L, 0L, category = "health")
        val viewModel = buildViewModel(initialHeadlines = listOf(world, business, health))

        viewModel.onIntent(HeadlinesContract.Intent.CategoryToggled("business"))
        viewModel.onIntent(HeadlinesContract.Intent.CategoryToggled("health"))

        val state = viewModel.state.value as HeadlinesContract.UiState.Success
        assertEquals(setOf("business", "health"), state.selectedCategories)
        assertEquals(setOf(2L, 3L), state.headlines.map { it.id }.toSet())
    }

    @Test
    fun selectingCategoryWithNoMatchesShowsEmptyHeadlines() = runTest(testDispatcher) {
        val world = Headline(1L, "World News", "Reuters", "https://example.com/1", null, 0L, 0L, category = "world")
        val viewModel = buildViewModel(initialHeadlines = listOf(world))

        viewModel.onIntent(HeadlinesContract.Intent.CategoryToggled("science"))

        val state = viewModel.state.value as HeadlinesContract.UiState.Success
        assertTrue(state.headlines.isEmpty())
    }

    @Test
    fun categorySelectionPersistsAcrossFlowCollection() = runTest(testDispatcher) {
        val categoryPreferencesRepository = HeadlineCategoryPreferencesRepository(FakeCategoryPreferencesDataStore())
        val world = Headline(1L, "World News", "Reuters", "https://example.com/1", null, 0L, 0L, category = "world")
        val viewModel = buildViewModel(
            initialHeadlines = listOf(world),
            categoryPreferencesRepository = categoryPreferencesRepository,
        )

        viewModel.onIntent(HeadlinesContract.Intent.CategoryToggled("world"))

        assertEquals(setOf("world"), categoryPreferencesRepository.selectedCategories.first())
    }

    @Test
    fun readHeadlineRendersWithMatchDataInSavedCardStyle() = runTest(testDispatcher) {
        val headline = Headline(
            id = 1L,
            title = "Breaking News",
            source = "Reuters",
            url = "https://example.com",
            imageUrl = null,
            publishedAt = 0L,
            fetchedAt = 0L,
            isRead = true,
        )
        val encouragement = Encouragement(
            summary = null,
            quoteText = "Our heart is restless",
            figureName = "Augustine",
            figureRole = "Bishop of Hippo",
            scriptureReference = "Confessions 1.1",
            scriptureText = "You have made us for yourself",
            explanation = "Matches because of themes of rest.",
            connectionThemes = listOf("rest"),
            matchTheme = "rest",
            tone = "hopeful",
            articleUrl = "https://example.com",
            bookmarked = true,
        )
        val viewModel = buildViewModel(
            initialHeadlines = listOf(headline),
            encouragementRepository = FakeEncouragementRepository(listOf(encouragement)),
        )

        val state = viewModel.state.value as HeadlinesContract.UiState.Success
        val item = state.headlines.single()
        assertTrue(item.isRead)
        assertEquals("Augustine", item.figureName)
        assertTrue(item.isBookmarked)
    }

    private fun buildViewModel(
        initialHeadlines: List<Headline> = emptyList(),
        headlinesFlow: MutableStateFlow<List<Headline>>? = null,
        headlineRepository: FakeHeadlineRepository = FakeHeadlineRepository(initialHeadlines, headlinesFlow),
        encouragementRepository: EncouragementRepository = FakeEncouragementRepository(emptyList()),
        categoryPreferencesRepository: HeadlineCategoryPreferencesRepository =
            HeadlineCategoryPreferencesRepository(FakeCategoryPreferencesDataStore()),
    ) = HeadlinesViewModel(
        headlineRepository = headlineRepository,
        encouragementRepository = encouragementRepository,
        getHeadlinesFeed = GetHeadlinesFeedUseCase(headlineRepository, encouragementRepository),
        categoryPreferencesRepository = categoryPreferencesRepository,
    )
}

private class FakeCategoryPreferencesDataStore : DataStore<Preferences> {
    private val state = MutableStateFlow(emptyPreferences())
    override val data: Flow<Preferences> = state
    override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences {
        val updated = transform(state.value)
        state.value = updated
        return updated
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

    override suspend fun markAsRead(url: String) = Unit
}

private class FakeEncouragementRepository(
    private val encouragements: List<Encouragement>
) : EncouragementRepository {
    override val isResolved: StateFlow<Boolean> = MutableStateFlow(true)

    override suspend fun resolve(userId: String?) = Unit

    override suspend fun getEncouragement(
        headlineTitle: String,
        headlineSource: String,
        headlineImageUrl: String?,
        articleUrl: String?,
        articleSnippet: String?,
        headlineCategory: String,
        headlinePublishedAt: Long,
    ): Encouragement = error("not used in these tests")

    override fun observeAll(): Flow<List<Encouragement>> = MutableStateFlow(encouragements)
    override fun observeBookmarked(): Flow<List<Encouragement>> = MutableStateFlow(encouragements.filter { it.bookmarked })
    override fun observeCountByFigureName(): Flow<Map<String, Int>> = MutableStateFlow(emptyMap())
    override fun observeByFigureId(figureId: Long): Flow<List<Encouragement>> = MutableStateFlow(emptyList())
    override fun observeIsBookmarked(articleUrl: String): Flow<Boolean> =
        MutableStateFlow(encouragements.any { it.articleUrl == articleUrl && it.bookmarked })
    override suspend fun toggleBookmark(articleUrl: String) = Unit
    override fun observeByEpochDay(epochDay: Long): Flow<List<Encouragement>> = MutableStateFlow(emptyList())
    override fun observeActiveEpochDays(): Flow<Set<Long>> = MutableStateFlow(emptySet())
}
