@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.mediasage.feature.history

import com.mediasage.data.local.dao.EncouragementDao
import com.mediasage.data.local.entity.EncouragementEntity
import com.mediasage.data.local.entity.VoiceFigureProjection
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
        val vm = HistoryViewModel(encouragementDao = FakeEncouragementDao(emptyList()))

        assertIs<HistoryContract.UiState.Empty>(vm.state.value)
    }

    @Test
    fun emitsSuccessWithItemsWhenEncouragementsCached() = runTest(testDispatcher) {
        val entity = buildEncouragementEntity(
            articleUrl = "https://example.com/article",
            headlineTitle = "Big News",
            figureName = "Augustine",
            figureRole = "Bishop of Hippo",
            quoteText = "Our heart is restless until it finds rest in You."
        )
        val vm = HistoryViewModel(encouragementDao = FakeEncouragementDao(listOf(entity)))

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
        val entity = buildEncouragementEntity(quoteText = longQuote)
        val vm = HistoryViewModel(encouragementDao = FakeEncouragementDao(listOf(entity)))

        val state = assertIs<HistoryContract.UiState.Success>(vm.state.value)
        assertEquals(120, state.items.first().quotePreview.length)
    }

    @Test
    fun headlineImageUrlIsReadFromEncouragementEntity() = runTest(testDispatcher) {
        val entity = buildEncouragementEntity(
            headlineImageUrl = "https://img.example.com/photo.jpg"
        )
        val vm = HistoryViewModel(encouragementDao = FakeEncouragementDao(listOf(entity)))

        val state = assertIs<HistoryContract.UiState.Success>(vm.state.value)
        assertEquals("https://img.example.com/photo.jpg", state.items.first().headlineImageUrl)
    }

    @Test
    fun headlineImageUrlIsNullWhenNotStoredInEntity() = runTest(testDispatcher) {
        val entity = buildEncouragementEntity(headlineImageUrl = null)
        val vm = HistoryViewModel(encouragementDao = FakeEncouragementDao(listOf(entity)))

        val state = assertIs<HistoryContract.UiState.Success>(vm.state.value)
        assertNull(state.items.first().headlineImageUrl)
    }

    @Test
    fun stateUpdatesWhenEncouragementFlowEmitsNewValues() = runTest(testDispatcher) {
        val flow = MutableStateFlow(emptyList<EncouragementEntity>())
        val vm = HistoryViewModel(encouragementDao = FakeEncouragementDao(flow = flow))

        assertIs<HistoryContract.UiState.Empty>(vm.state.value)

        flow.value = listOf(buildEncouragementEntity())

        assertIs<HistoryContract.UiState.Success>(vm.state.value)
    }
}

private fun buildEncouragementEntity(
    articleUrl: String = "https://example.com",
    headlineTitle: String = "Test Headline",
    figureName: String = "Augustine",
    figureRole: String = "Bishop",
    quoteText: String = "Test quote",
    headlineImageUrl: String? = null
) = EncouragementEntity(
    articleUrl = articleUrl,
    summary = null,
    quoteText = quoteText,
    figureName = figureName,
    figureRole = figureRole,
    scriptureReference = "John 3:16",
    scriptureText = "For God so loved the world",
    explanation = "Explanation",
    connectionThemes = "faith",
    matchTheme = "hope",
    tone = "gentle",
    figureImageUrl = null,
    headlineTitle = headlineTitle,
    headlineImageUrl = headlineImageUrl,
    cachedAt = 1000L
)

private class FakeEncouragementDao(
    initialEntities: List<EncouragementEntity> = emptyList(),
    flow: MutableStateFlow<List<EncouragementEntity>>? = null
) : EncouragementDao {

    private val _flow = flow ?: MutableStateFlow(initialEntities)

    override fun getAll(): Flow<List<EncouragementEntity>> = _flow

    override suspend fun insert(encouragement: EncouragementEntity) = Unit

    override suspend fun getByArticleUrl(articleUrl: String): EncouragementEntity? =
        _flow.value.find { it.articleUrl == articleUrl }

    override fun getDistinctFigures(): Flow<List<VoiceFigureProjection>> =
        MutableStateFlow(emptyList())

    override fun getByFigureName(figureName: String): Flow<List<EncouragementEntity>> =
        MutableStateFlow(_flow.value.filter { it.figureName == figureName })

    override suspend fun getRecentFigureNames(limit: Int): List<String> = emptyList()

    override suspend fun deleteAll() = Unit
}
