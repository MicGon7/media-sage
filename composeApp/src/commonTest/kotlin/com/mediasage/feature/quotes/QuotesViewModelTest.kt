@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.mediasage.feature.quotes

import com.mediasage.domain.model.Figure
import com.mediasage.domain.model.FigureCategory
import com.mediasage.domain.model.Quote
import com.mediasage.domain.repository.FigureRepository
import com.mediasage.domain.repository.QuoteRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
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
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class QuotesViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    private val lewis = Figure(id = 1L, name = "C.S. Lewis", category = FigureCategory.THEOLOGIAN, century = "20th")
    private val julian = Figure(id = 2L, name = "Julian of Norwich", category = FigureCategory.MYSTIC, century = "14th")

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun quotesAreGroupedByFigureSortedAlphabeticallyByFigureName() = runTest(testDispatcher) {
        val quotes = listOf(
            Quote(id = 1L, figureId = 2L, text = "All shall be well.", source = "Revelations", themes = emptyList()),
            Quote(id = 2L, figureId = 1L, text = "You are never too old to set another goal.", source = "", themes = emptyList()),
        )
        val viewModel = quotesViewModel(quotes = quotes, figures = listOf(lewis, julian))

        val state = viewModel.state.value as QuotesContract.UiState.Success

        assertEquals(listOf("C.S. Lewis", "Julian of Norwich"), state.sections.map { it.figureName })
    }

    @Test
    fun memorizedQuoteIsMarkedWithinItsFigureSection() = runTest(testDispatcher) {
        val memorized = Quote(
            id = 1L,
            figureId = 1L,
            text = "You are never too old to set another goal.",
            source = "",
            themes = emptyList(),
            memorized = true,
        )
        val other = Quote(id = 2L, figureId = 1L, text = "Hardships often prepare ordinary people.", source = "", themes = emptyList())
        val viewModel = quotesViewModel(quotes = listOf(memorized, other), figures = listOf(lewis))

        val state = viewModel.state.value as QuotesContract.UiState.Success

        val section = state.sections.first { it.figureId == 1L }
        assertTrue(section.quotes.first { it.quoteText == memorized.text }.isMemorized)
        assertFalse(section.quotes.first { it.quoteText == other.text }.isMemorized)
    }

    @Test
    fun quoteSelectedMemorizesTheQuoteForItsFigure() = runTest(testDispatcher) {
        val quote = Quote(id = 1L, figureId = 1L, text = "You are never too old to set another goal.", source = "", themes = emptyList())
        val quoteRepo = FakeQuoteRepositoryForQuotesScreen(quotes = listOf(quote))
        val viewModel = quotesViewModel(quoteRepository = quoteRepo, figures = listOf(lewis))

        viewModel.onIntent(QuotesContract.Intent.QuoteSelected(figureId = 1L, quoteText = quote.text))

        assertEquals(listOf(1L to quote.text), quoteRepo.memorizeCalls)
    }

    private fun TestScope.quotesViewModel(
        quotes: List<Quote> = emptyList(),
        figures: List<Figure> = emptyList(),
        quoteRepository: FakeQuoteRepositoryForQuotesScreen = FakeQuoteRepositoryForQuotesScreen(quotes),
    ): QuotesViewModel {
        val viewModel = QuotesViewModel(
            quoteRepository = quoteRepository,
            figureRepository = FakeFigureRepositoryForQuotesScreen(figures),
        )
        backgroundScope.launch(testDispatcher) { viewModel.state.collect {} }
        return viewModel
    }
}

private class FakeFigureRepositoryForQuotesScreen(private val figures: List<Figure>) : FigureRepository {
    override fun observeAllFigures(): Flow<List<Figure>> = MutableStateFlow(figures)
    override fun observeFiguresByCategory(category: FigureCategory): Flow<List<Figure>> = MutableStateFlow(emptyList())
    override suspend fun getFigureById(id: Long): Figure? = figures.firstOrNull { it.id == id }
    override suspend fun getFigureByName(name: String): Figure? = figures.firstOrNull { it.name == name }
    override suspend fun syncFigures() = Unit
}

private class FakeQuoteRepositoryForQuotesScreen(
    private val quotes: List<Quote> = emptyList(),
) : QuoteRepository {
    val memorizeCalls = mutableListOf<Pair<Long, String>>()
    override fun observeAllQuotes(): Flow<List<Quote>> = MutableStateFlow(quotes)
    override fun observeQuotesByFigure(figureId: Long): Flow<List<Quote>> =
        MutableStateFlow(quotes.filter { it.figureId == figureId })
    override suspend fun getQuoteById(id: Long): Quote? = quotes.firstOrNull { it.id == id }
    override suspend fun getLatestQuoteForFigure(figureId: Long): Quote? = quotes.lastOrNull { it.figureId == figureId }
    override suspend fun saveQuote(text: String, source: String, themes: List<String>, figureId: Long) = Unit
    override fun observeMemorizedQuote(): Flow<Quote?> = MutableStateFlow(null)
    override suspend fun memorizeQuote(figureId: Long, text: String) {
        memorizeCalls.add(figureId to text)
    }
    override val isResolved: StateFlow<Boolean> = MutableStateFlow(true)
    override suspend fun resolve(userId: String?) = Unit
}
