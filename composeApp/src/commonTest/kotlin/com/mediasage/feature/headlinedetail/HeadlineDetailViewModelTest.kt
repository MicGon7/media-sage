@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.mediasage.feature.headlinedetail

import com.mediasage.domain.model.Encouragement
import com.mediasage.domain.model.Figure
import com.mediasage.domain.model.FigureCategory
import com.mediasage.domain.model.Headline
import com.mediasage.domain.repository.EncouragementRepository
import com.mediasage.domain.repository.FigureRepository
import com.mediasage.domain.repository.HeadlineRepository
import com.mediasage.domain.repository.QuoteRepository
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

class HeadlineDetailViewModelTest {

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
    fun emitsSuccessAfterSuccessfulMatch() = runTest(testDispatcher) {
        val vm = buildViewModel(
            headline = buildHeadline(),
            encouragement = buildEncouragement(figureName = "Augustine"),
            figure = buildFigure(name = "Augustine"),
        )

        assertIs<HeadlineDetailContract.UiState.Success>(vm.state.value)
    }

    @Test
    fun savesQuoteWithCorrectParametersWhenFigureIsFound() = runTest(testDispatcher) {
        val figure = buildFigure(id = 42L, name = "Augustine")
        val encouragement = buildEncouragement(
            figureName = "Augustine",
            quoteText = "Our heart is restless",
            scriptureReference = "Confessions 1.1",
            connectionThemes = listOf("rest", "longing"),
        )
        val quoteRepo = FakeQuoteRepository()

        buildViewModel(
            headline = buildHeadline(),
            encouragement = encouragement,
            figure = figure,
            quoteRepository = quoteRepo,
        )

        assertEquals(1, quoteRepo.savedQuotes.size)
        val saved = quoteRepo.savedQuotes.first()
        assertEquals("Our heart is restless", saved.text)
        assertEquals("Confessions 1.1", saved.source)
        assertEquals(listOf("rest", "longing"), saved.themes)
        assertEquals(42L, saved.figureId)
    }

    @Test
    fun doesNotSaveQuoteWhenFigureIsNotFound() = runTest(testDispatcher) {
        val quoteRepo = FakeQuoteRepository()

        buildViewModel(
            headline = buildHeadline(),
            encouragement = buildEncouragement(figureName = "Unknown Figure"),
            figure = null,
            quoteRepository = quoteRepo,
        )

        assertEquals(0, quoteRepo.savedQuotes.size)
    }

    @Test
    fun stateRemainsSuccessWhenQuoteSaveFails() = runTest(testDispatcher) {
        val quoteRepo = FakeQuoteRepository(throwOnSave = true)

        val vm = buildViewModel(
            headline = buildHeadline(),
            encouragement = buildEncouragement(figureName = "Augustine"),
            figure = buildFigure(name = "Augustine"),
            quoteRepository = quoteRepo,
        )

        assertIs<HeadlineDetailContract.UiState.Success>(vm.state.value)
    }

    @Test
    fun emitsErrorWhenEncouragementFetchFails() = runTest(testDispatcher) {
        val vm = buildViewModel(
            headline = buildHeadline(),
            encouragement = null,
        )

        assertIs<HeadlineDetailContract.UiState.Error>(vm.state.value)
    }

    private fun buildViewModel(
        headline: Headline? = null,
        encouragement: Encouragement? = buildEncouragement(),
        figure: Figure? = null,
        quoteRepository: QuoteRepository = FakeQuoteRepository(),
        articleUrl: String = "https://example.com/article",
    ) = HeadlineDetailViewModel(
        articleUrl = articleUrl,
        headlineRepository = FakeHeadlineRepository(headline),
        encouragementRepository = FakeEncouragementRepository(encouragement),
        figureRepository = FakeFigureRepository(figure),
        quoteRepository = quoteRepository,
    )
}

private fun buildHeadline(url: String = "https://example.com/article") = Headline(
    id = 1L,
    title = "Test Headline",
    source = "Reuters",
    url = url,
    imageUrl = null,
    publishedAt = 0L,
    fetchedAt = 0L,
)

private fun buildFigure(id: Long = 1L, name: String = "Augustine") = Figure(
    id = id,
    name = name,
    category = FigureCategory.THEOLOGIAN,
    century = "4th",
)

private fun buildEncouragement(
    figureName: String = "Augustine",
    quoteText: String = "Our heart is restless until it rests in Thee",
    scriptureReference: String = "Confessions 1.1",
    connectionThemes: List<String> = listOf("peace"),
) = Encouragement(
    summary = null,
    quoteText = quoteText,
    figureName = figureName,
    figureRole = "Bishop of Hippo",
    scriptureReference = scriptureReference,
    scriptureText = "You have made us for yourself",
    explanation = "Matches because of themes of rest.",
    connectionThemes = connectionThemes,
    matchTheme = "peace",
    tone = "hopeful",
)

private data class SavedQuote(
    val text: String,
    val source: String,
    val themes: List<String>,
    val figureId: Long,
)

private class FakeQuoteRepository(private val throwOnSave: Boolean = false) : QuoteRepository {
    val savedQuotes = mutableListOf<SavedQuote>()

    override fun observeAllQuotes(): Flow<List<com.mediasage.domain.model.Quote>> = flowOf(emptyList())
    override fun observeQuotesByFigure(figureId: Long): Flow<List<com.mediasage.domain.model.Quote>> = flowOf(emptyList())
    override suspend fun getQuoteById(id: Long): com.mediasage.domain.model.Quote? = null
    override suspend fun getLatestQuoteForFigure(figureId: Long): com.mediasage.domain.model.Quote? = null

    override suspend fun saveQuote(text: String, source: String, themes: List<String>, figureId: Long) {
        if (throwOnSave) error("simulated save failure")
        savedQuotes.add(SavedQuote(text, source, themes, figureId))
    }
}

private class FakeHeadlineRepository(private val headline: Headline?) : HeadlineRepository {
    override fun observeHeadlines(): Flow<List<Headline>> = flowOf(listOfNotNull(headline))
    override suspend fun getHeadlineById(id: Long): Headline? = headline?.takeIf { it.id == id }
    override suspend fun getHeadlineByUrl(url: String): Headline? = headline?.takeIf { it.url == url }
    override suspend fun refreshHeadlines() = Unit
    override suspend fun clearOldHeadlines(olderThanMillis: Long) = Unit
}

private class FakeEncouragementRepository(private val encouragement: Encouragement?) : EncouragementRepository {
    override suspend fun getEncouragement(
        headlineTitle: String,
        headlineSource: String,
        headlineImageUrl: String?,
        articleUrl: String?,
        articleSnippet: String?,
    ): Encouragement = encouragement ?: error("simulated encouragement failure")

    override fun observeAll(): Flow<List<Encouragement>> = MutableStateFlow(emptyList())
    override fun observeBookmarked(): Flow<List<Encouragement>> = MutableStateFlow(emptyList())
    override fun observeCountByFigureName(): Flow<Map<String, Int>> = MutableStateFlow(emptyMap())
    override fun observeByFigureId(figureId: Long): Flow<List<Encouragement>> = MutableStateFlow(emptyList())
    override fun observeIsBookmarked(articleUrl: String): Flow<Boolean> = MutableStateFlow(false)
    override fun observeByEpochDay(epochDay: Long): Flow<List<Encouragement>> = MutableStateFlow(emptyList())
    override fun observeActiveEpochDays(): Flow<Set<Long>> = MutableStateFlow(emptySet())
    override suspend fun toggleBookmark(articleUrl: String) = Unit
}

private class FakeFigureRepository(private val figure: Figure?) : FigureRepository {
    override fun observeAllFigures(): Flow<List<Figure>> = flowOf(listOfNotNull(figure))
    override fun observeFiguresByCategory(category: FigureCategory): Flow<List<Figure>> = flowOf(listOfNotNull(figure))
    override suspend fun getFigureById(id: Long): Figure? = figure?.takeIf { it.id == id }
    override suspend fun getFigureByName(name: String): Figure? = figure?.takeIf { it.name == name }
    override suspend fun syncFigures() = Unit
}
