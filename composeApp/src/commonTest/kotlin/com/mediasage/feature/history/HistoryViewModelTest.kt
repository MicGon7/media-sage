@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.mediasage.feature.history

import com.mediasage.data.local.dao.EncouragementDao
import com.mediasage.data.local.dao.HeadlineDao
import com.mediasage.data.local.entity.EncouragementEntity
import com.mediasage.data.local.entity.HeadlineEntity
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
        val vm = HistoryViewModel(
            encouragementDao = FakeEncouragementDao(emptyList()),
            headlineDao = FakeHeadlineDao()
        )

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
        val vm = HistoryViewModel(
            encouragementDao = FakeEncouragementDao(listOf(entity)),
            headlineDao = FakeHeadlineDao()
        )

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
        val vm = HistoryViewModel(
            encouragementDao = FakeEncouragementDao(listOf(entity)),
            headlineDao = FakeHeadlineDao()
        )

        val state = assertIs<HistoryContract.UiState.Success>(vm.state.value)
        assertEquals(120, state.items.first().quotePreview.length)
    }

    @Test
    fun headlineIdIsPopulatedWhenHeadlineExistsForUrl() = runTest(testDispatcher) {
        val articleUrl = "https://example.com/article"
        val entity = buildEncouragementEntity(articleUrl = articleUrl)
        val headlineDao = FakeHeadlineDao(urlToIdMap = mapOf(articleUrl to 42L))
        val vm = HistoryViewModel(
            encouragementDao = FakeEncouragementDao(listOf(entity)),
            headlineDao = headlineDao
        )

        val state = assertIs<HistoryContract.UiState.Success>(vm.state.value)
        assertEquals(42L, state.items.first().headlineId)
    }

    @Test
    fun headlineIdIsNullWhenHeadlineNotFoundForUrl() = runTest(testDispatcher) {
        val entity = buildEncouragementEntity(articleUrl = "https://example.com/gone")
        val vm = HistoryViewModel(
            encouragementDao = FakeEncouragementDao(listOf(entity)),
            headlineDao = FakeHeadlineDao()
        )

        val state = assertIs<HistoryContract.UiState.Success>(vm.state.value)
        assertNull(state.items.first().headlineId)
    }

    @Test
    fun imageUrlIsPopulatedFromHeadline() = runTest(testDispatcher) {
        val articleUrl = "https://example.com/article"
        val entity = buildEncouragementEntity(articleUrl = articleUrl)
        val headlineDao = FakeHeadlineDao(
            urlToIdMap = mapOf(articleUrl to 42L),
            urlToImageMap = mapOf(articleUrl to "https://img.example.com/photo.jpg")
        )
        val vm = HistoryViewModel(
            encouragementDao = FakeEncouragementDao(listOf(entity)),
            headlineDao = headlineDao
        )

        val state = assertIs<HistoryContract.UiState.Success>(vm.state.value)
        assertEquals("https://img.example.com/photo.jpg", state.items.first().imageUrl)
    }

    @Test
    fun imageUrlIsNullWhenHeadlineNotFound() = runTest(testDispatcher) {
        val entity = buildEncouragementEntity(articleUrl = "https://example.com/gone")
        val vm = HistoryViewModel(
            encouragementDao = FakeEncouragementDao(listOf(entity)),
            headlineDao = FakeHeadlineDao()
        )

        val state = assertIs<HistoryContract.UiState.Success>(vm.state.value)
        assertNull(state.items.first().imageUrl)
    }

    @Test
    fun stateUpdatesWhenEncouragementFlowEmitsNewValues() = runTest(testDispatcher) {
        val flow = MutableStateFlow(emptyList<EncouragementEntity>())
        val vm = HistoryViewModel(
            encouragementDao = FakeEncouragementDao(flow = flow),
            headlineDao = FakeHeadlineDao()
        )

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
    quoteText: String = "Test quote"
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

private class FakeHeadlineDao(
    private val urlToIdMap: Map<String, Long> = emptyMap(),
    private val urlToImageMap: Map<String, String> = emptyMap()
) : HeadlineDao {

    override suspend fun getIdByUrl(url: String): Long? = urlToIdMap[url]

    override suspend fun getByUrl(url: String): HeadlineEntity? {
        val id = urlToIdMap[url] ?: return null
        return HeadlineEntity(
            id = id,
            title = "",
            source = "",
            url = url,
            imageUrl = urlToImageMap[url],
            publishedAt = 0L,
            fetchedAt = 0L
        )
    }

    override suspend fun insert(headline: HeadlineEntity): Long = 0L

    override suspend fun insertAll(headlines: List<HeadlineEntity>) = Unit

    override fun getAll(): Flow<List<HeadlineEntity>> = MutableStateFlow(emptyList())

    override suspend fun getById(id: Long): HeadlineEntity? = null

    override suspend fun deleteOlderThan(olderThan: Long) = Unit

    override suspend fun deleteAll() = Unit
}
