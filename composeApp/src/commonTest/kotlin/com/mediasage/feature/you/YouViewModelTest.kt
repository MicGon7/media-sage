@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.mediasage.feature.you

import com.mediasage.domain.model.Figure
import com.mediasage.domain.model.FigureCategory
import com.mediasage.domain.model.Quote
import com.mediasage.domain.repository.DayAssignmentRepository
import com.mediasage.domain.repository.FigureRepository
import com.mediasage.domain.repository.QuoteRepository
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
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class YouViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    private val testFigure = Figure(
        id = 1L,
        name = "Augustine of Hippo",
        category = FigureCategory.CHURCH_FATHER,
        century = "4th",
        role = "Bishop of Hippo"
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
    fun quoteCardIsPopulatedWhenSavedQuoteExistsForTodaysFigure() = runTest(testDispatcher) {
        val savedQuote = Quote(id = 1L, figureId = 1L, text = "Our heart is restless.", source = "Confessions", themes = emptyList())
        val viewModel = buildViewModel(
            figure = testFigure,
            todayAssignment = mapOf(0 to testFigure.id, 1 to testFigure.id, 2 to testFigure.id, 3 to testFigure.id, 4 to testFigure.id, 5 to testFigure.id, 6 to testFigure.id),
            latestQuote = savedQuote
        )

        val state = viewModel.state.value as YouContract.UiState.Ready
        assertNotNull(state.quoteCard)
        assertEquals("Our heart is restless.", state.quoteCard?.quoteText)
        assertEquals("Augustine of Hippo", state.quoteCard?.figureName)
    }

    @Test
    fun quoteCardIsNullWhenNoSavedQuoteExistsForTodaysFigure() = runTest(testDispatcher) {
        val viewModel = buildViewModel(
            figure = testFigure,
            todayAssignment = mapOf(0 to testFigure.id, 1 to testFigure.id, 2 to testFigure.id, 3 to testFigure.id, 4 to testFigure.id, 5 to testFigure.id, 6 to testFigure.id),
            latestQuote = null
        )

        val state = viewModel.state.value as YouContract.UiState.Ready
        assertNull(state.quoteCard)
    }

    @Test
    fun quoteCardIsNullWhenNoFigureAssignedForToday() = runTest(testDispatcher) {
        val savedQuote = Quote(id = 1L, figureId = 1L, text = "Our heart is restless.", source = "Confessions", themes = emptyList())
        val viewModel = buildViewModel(
            figure = testFigure,
            todayAssignment = emptyMap(),
            latestQuote = savedQuote
        )

        val state = viewModel.state.value as YouContract.UiState.Ready
        assertNull(state.quoteCard)
    }

    private fun buildViewModel(
        figure: Figure,
        todayAssignment: Map<Int, Long>,
        latestQuote: Quote?
    ): YouViewModel = YouViewModel(
        figureRepository = FakeFigureRepository(figure),
        dayAssignmentRepository = FakeDayAssignmentRepository(MutableStateFlow(todayAssignment)),
        quoteRepository = FakeQuoteRepository(latestQuote)
    )
}

private class FakeFigureRepository(private val figure: Figure) : FigureRepository {
    private val flow = MutableStateFlow(listOf(figure))
    override fun observeAllFigures(): Flow<List<Figure>> = flow
    override fun observeFiguresByCategory(category: FigureCategory): Flow<List<Figure>> = MutableStateFlow(emptyList())
    override suspend fun getFigureById(id: Long): Figure? = if (figure.id == id) figure else null
    override suspend fun getFigureByName(name: String): Figure? = if (figure.name == name) figure else null
    override suspend fun syncFigures() = Unit
}

private class FakeDayAssignmentRepository(
    private val assignmentsFlow: MutableStateFlow<Map<Int, Long>>
) : DayAssignmentRepository {
    override fun observeAssignments(): Flow<Map<Int, Long>> = assignmentsFlow
    override suspend fun assign(dayOfWeek: Int, figureId: Long) = Unit
    override suspend fun clear(dayOfWeek: Int) = Unit
}

private class FakeQuoteRepository(private val latestQuote: Quote?) : QuoteRepository {
    override fun observeAllQuotes(): Flow<List<Quote>> = MutableStateFlow(listOfNotNull(latestQuote))
    override fun observeQuotesByFigure(figureId: Long): Flow<List<Quote>> = MutableStateFlow(listOfNotNull(latestQuote))
    override suspend fun getQuoteById(id: Long): Quote? = latestQuote?.takeIf { it.id == id }
    override suspend fun getLatestQuoteForFigure(figureId: Long): Quote? = latestQuote
}
