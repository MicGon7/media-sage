@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.mediasage.feature.history

import com.mediasage.domain.model.Encouragement
import com.mediasage.domain.repository.EncouragementRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
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

    @Test
    fun emitsEmptyWhenNoEncouragements() = runTest(testDispatcher) {
        val vm = HistoryViewModel(encouragementRepository = FakeEncouragementRepository(emptyList()))

        assertIs<HistoryContract.UiState.Empty>(vm.state.value)
    }

    @Test
    fun emitsSuccessWithItemsWhenEncouragementsCached() = runTest(testDispatcher) {
        val encouragement = buildEncouragement(
            articleUrl = "https://example.com/article",
            headlineTitle = "Big News",
            figureName = "Augustine",
            figureRole = "Bishop of Hippo",
            quoteText = "Our heart is restless until it finds rest in You."
        )
        val vm = HistoryViewModel(encouragementRepository = FakeEncouragementRepository(listOf(encouragement)))

        val state = assertIs<HistoryContract.UiState.Success>(vm.state.value)
        assertEquals(1, state.items.size)
        val item = state.items.first()
        assertEquals("Big News", item.headlineTitle)
        assertEquals("Augustine", item.figureName)
        assertEquals("Bishop of Hippo", item.figureRole)
    }

    @Test
    fun quotePreviewIsTruncatedTo120Chars() = runTest(testDispatcher) {
        val longQuote = "A".repeat(200)
        val encouragement = buildEncouragement(quoteText = longQuote)
        val vm = HistoryViewModel(encouragementRepository = FakeEncouragementRepository(listOf(encouragement)))

        val state = assertIs<HistoryContract.UiState.Success>(vm.state.value)
        assertEquals(120, state.items.first().quotePreview.length)
    }

    @Test
    fun headlineImageUrlIsReadFromEncouragement() = runTest(testDispatcher) {
        val encouragement = buildEncouragement(headlineImageUrl = "https://img.example.com/photo.jpg")
        val vm = HistoryViewModel(encouragementRepository = FakeEncouragementRepository(listOf(encouragement)))

        val state = assertIs<HistoryContract.UiState.Success>(vm.state.value)
        assertEquals("https://img.example.com/photo.jpg", state.items.first().headlineImageUrl)
    }

    @Test
    fun headlineImageUrlIsNullWhenNotStored() = runTest(testDispatcher) {
        val encouragement = buildEncouragement(headlineImageUrl = null)
        val vm = HistoryViewModel(encouragementRepository = FakeEncouragementRepository(listOf(encouragement)))

        val state = assertIs<HistoryContract.UiState.Success>(vm.state.value)
        assertNull(state.items.first().headlineImageUrl)
    }

    @Test
    fun stateUpdatesWhenEncouragementFlowEmitsNewValues() = runTest(testDispatcher) {
        val flow = MutableStateFlow(emptyList<Encouragement>())
        val vm = HistoryViewModel(encouragementRepository = FakeEncouragementRepository(flow = flow))

        assertIs<HistoryContract.UiState.Empty>(vm.state.value)

        flow.value = listOf(buildEncouragement())

        assertIs<HistoryContract.UiState.Success>(vm.state.value)
    }
}

private fun buildEncouragement(
    articleUrl: String = "https://example.com",
    headlineTitle: String = "Test Headline",
    figureName: String = "Augustine",
    figureRole: String = "Bishop",
    quoteText: String = "Test quote",
    headlineImageUrl: String? = null,
    bookmarked: Boolean = false
) = Encouragement(
    articleUrl = articleUrl,
    summary = null,
    quoteText = quoteText,
    figureName = figureName,
    figureRole = figureRole,
    scriptureReference = "John 3:16",
    scriptureText = "For God so loved the world",
    explanation = "Explanation",
    connectionThemes = listOf("faith"),
    matchTheme = "hope",
    tone = "gentle",
    figureImageUrl = null,
    headlineTitle = headlineTitle,
    headlineImageUrl = headlineImageUrl,
    bookmarked = bookmarked
)

private class FakeEncouragementRepository(
    initialEncouragements: List<Encouragement> = emptyList(),
    flow: MutableStateFlow<List<Encouragement>>? = null
) : EncouragementRepository {

    private val _flow = flow ?: MutableStateFlow(initialEncouragements)

    override fun observeAll(): Flow<List<Encouragement>> = _flow

    override fun observeBookmarked(): Flow<List<Encouragement>> =
        MutableStateFlow(_flow.value.filter { it.bookmarked })

    override fun observeCountByFigureName(): Flow<Map<String, Int>> =
        MutableStateFlow(_flow.value.groupBy { it.figureName }.mapValues { (_, v) -> v.size })

    override fun observeByFigureId(figureId: Long): Flow<List<Encouragement>> =
        MutableStateFlow(emptyList())

    override fun observeIsBookmarked(articleUrl: String): Flow<Boolean> =
        MutableStateFlow(_flow.value.find { it.articleUrl == articleUrl }?.bookmarked ?: false)

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
    ): Encouragement = _flow.value.first()

    override val isResolved: StateFlow<Boolean> = MutableStateFlow(true)
    override suspend fun resolve(userId: String?) = Unit
}
