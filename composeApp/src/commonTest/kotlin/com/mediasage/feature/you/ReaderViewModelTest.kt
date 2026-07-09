@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.mediasage.feature.you

import com.mediasage.domain.model.BriefingDay
import com.mediasage.domain.model.DailyReflection
import com.mediasage.domain.model.DayAssignment
import com.mediasage.domain.model.Figure
import com.mediasage.domain.model.FigureCategory
import com.mediasage.domain.model.LensFilter
import com.mediasage.domain.model.Quote
import com.mediasage.domain.model.Encouragement
import com.mediasage.domain.repository.DailyReflectionRepository
import com.mediasage.domain.repository.DayAssignmentRepository
import com.mediasage.domain.repository.EncouragementRepository
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

class ReaderViewModelTest {

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
    fun quoteCardIsPopulatedWithMostRecentSavedQuote() = runTest(testDispatcher) {
        val savedQuote = Quote(id = 1L, figureId = 1L, text = "Our heart is restless.", source = "Confessions", themes = emptyList())
        val viewModel = buildViewModel(figure = testFigure, latestQuote = savedQuote)

        val state = viewModel.state.value as ReaderContract.UiState.Ready
        assertNotNull(state.quoteCard)
        assertEquals("Our heart is restless.", state.quoteCard?.quoteText)
        assertEquals("Augustine of Hippo", state.quoteCard?.figureName)
    }

    @Test
    fun quoteCardIsNullWhenNoQuotesSaved() = runTest(testDispatcher) {
        val viewModel = buildViewModel(figure = testFigure, latestQuote = null)

        val state = viewModel.state.value as ReaderContract.UiState.Ready
        assertNull(state.quoteCard)
    }

    @Test
    fun selectFutureDay_setsPickerOpenForEpochDay() = runTest(testDispatcher) {
        val viewModel = buildViewModel(figure = testFigure, latestQuote = null)
        val futureEpochDay = (viewModel.state.value as ReaderContract.UiState.Ready)
            .calendarDays.firstOrNull { it.isFuture }?.epochDay ?: return@runTest

        viewModel.onIntent(ReaderContract.Intent.SelectFutureDay(futureEpochDay))

        val state = viewModel.state.value as ReaderContract.UiState.Ready
        assertEquals(futureEpochDay, state.pickerOpenForEpochDay)
    }

    @Test
    fun assignOverride_clearsPickerOpenForEpochDay() = runTest(testDispatcher) {
        val viewModel = buildViewModel(figure = testFigure, latestQuote = null)
        val futureEpochDay = (viewModel.state.value as ReaderContract.UiState.Ready)
            .calendarDays.firstOrNull { it.isFuture }?.epochDay ?: return@runTest
        viewModel.onIntent(ReaderContract.Intent.SelectFutureDay(futureEpochDay))

        viewModel.onIntent(ReaderContract.Intent.AssignOverride(futureEpochDay, testFigure.id))

        val state = viewModel.state.value as ReaderContract.UiState.Ready
        assertNull(state.pickerOpenForEpochDay)
    }

    @Test
    fun clearOverride_clearsPickerOpenForEpochDay() = runTest(testDispatcher) {
        val viewModel = buildViewModel(figure = testFigure, latestQuote = null)
        val futureEpochDay = (viewModel.state.value as ReaderContract.UiState.Ready)
            .calendarDays.firstOrNull { it.isFuture }?.epochDay ?: return@runTest
        viewModel.onIntent(ReaderContract.Intent.SelectFutureDay(futureEpochDay))

        viewModel.onIntent(ReaderContract.Intent.ClearOverride(futureEpochDay))

        val state = viewModel.state.value as ReaderContract.UiState.Ready
        assertNull(state.pickerOpenForEpochDay)
    }

    private fun buildViewModel(figure: Figure, latestQuote: Quote?): ReaderViewModel = ReaderViewModel(
        figureRepository = FakeFigureRepository(figure),
        dayAssignmentRepository = FakeDayAssignmentRepository(MutableStateFlow(emptyMap())),
        quoteRepository = FakeQuoteRepository(latestQuote),
        dailyReflectionRepository = FakeDailyReflectionRepository(),
        encouragementRepository = FakeEncouragementRepository(),
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
    private val assignmentsFlow: MutableStateFlow<Map<Int, DayAssignment>>
) : DayAssignmentRepository {
    val overrides = mutableMapOf<Long, Long>()
    private val overridesFlow = MutableStateFlow<Map<Long, Long>>(emptyMap())

    override fun observeAssignments(): Flow<Map<Int, DayAssignment>> = assignmentsFlow
    override fun observeOverridesByEpochDayRange(start: Long, end: Long): Flow<Map<Long, Long>> = overridesFlow
    override suspend fun assign(dayOfWeek: Int, figureId: Long, lens: LensFilter?) = Unit
    override suspend fun clear(dayOfWeek: Int) = Unit
    override suspend fun seedDefaultsIfEmpty() = Unit
    override suspend fun setOverride(epochDay: Long, figureId: Long) {
        overrides[epochDay] = figureId
        overridesFlow.value = overrides.toMap()
    }
    override suspend fun clearOverride(epochDay: Long) {
        overrides.remove(epochDay)
        overridesFlow.value = overrides.toMap()
    }
    override suspend fun resolveReporter(epochDay: Long, dayOfWeek: Int): Long? = null
}

private class FakeDailyReflectionRepository : DailyReflectionRepository {
    override suspend fun getOrFetch(
        figureId: Long,
        figureName: String,
        headlines: List<String>,
        tone: String,
        theme: String?,
    ): DailyReflection = throw UnsupportedOperationException()
    override fun observeByEpochDayRange(startEpochDay: Long, endEpochDay: Long): Flow<List<BriefingDay>> =
        MutableStateFlow(emptyList())
    override suspend fun getForDay(epochDay: Long): DailyReflection? = null
}

private class FakeQuoteRepository(private val latestQuote: Quote?) : QuoteRepository {
    override fun observeAllQuotes(): Flow<List<Quote>> = MutableStateFlow(listOfNotNull(latestQuote))
    override fun observeQuotesByFigure(figureId: Long): Flow<List<Quote>> = MutableStateFlow(listOfNotNull(latestQuote))
    override suspend fun getQuoteById(id: Long): Quote? = latestQuote?.takeIf { it.id == id }
    override suspend fun getLatestQuoteForFigure(figureId: Long): Quote? = latestQuote
    override suspend fun saveQuote(text: String, source: String, themes: List<String>, figureId: Long) = Unit
}

private class FakeEncouragementRepository : EncouragementRepository {
    override suspend fun getEncouragement(
        headlineTitle: String,
        headlineSource: String,
        headlineImageUrl: String?,
        articleUrl: String?,
        articleSnippet: String?,
    ): Encouragement = throw UnsupportedOperationException()
    override fun observeAll(): Flow<List<Encouragement>> = MutableStateFlow(emptyList())
    override fun observeBookmarked(): Flow<List<Encouragement>> = MutableStateFlow(emptyList())
    override fun observeCountByFigureName(): Flow<Map<String, Int>> = MutableStateFlow(emptyMap())
    override fun observeByFigureId(figureId: Long): Flow<List<Encouragement>> = MutableStateFlow(emptyList())
    override fun observeIsBookmarked(articleUrl: String): Flow<Boolean> = MutableStateFlow(false)
    override suspend fun toggleBookmark(articleUrl: String) = Unit
    override fun observeByEpochDay(epochDay: Long): Flow<List<Encouragement>> = MutableStateFlow(emptyList())
    override fun observeActiveEpochDays(): Flow<Set<Long>> = MutableStateFlow(emptySet())
}
