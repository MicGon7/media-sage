@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.mediasage.feature.you

import com.mediasage.data.repository.epochMillis
import com.mediasage.domain.model.BriefingDay
import com.mediasage.domain.model.DailyReflection
import com.mediasage.domain.model.DayAssignment
import com.mediasage.domain.model.Figure
import com.mediasage.domain.model.FigureCategory
import com.mediasage.domain.model.LensFilter
import com.mediasage.domain.model.Quote
import com.mediasage.domain.model.UserSession
import com.mediasage.domain.repository.AuthRepository
import com.mediasage.domain.repository.DailyReflectionRepository
import com.mediasage.domain.repository.DayAssignmentRepository
import com.mediasage.domain.repository.FigureRepository
import com.mediasage.domain.repository.QuoteRepository
import com.mediasage.domain.usecase.GetReaderCalendarUseCase
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
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

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
        val viewModel = readerViewModel(figure = testFigure, latestQuote = savedQuote)

        val state = viewModel.state.value as ReaderContract.UiState.Ready
        assertNotNull(state.quoteCard)
        assertEquals("Our heart is restless.", state.quoteCard?.quoteText)
        assertEquals("Augustine of Hippo", state.quoteCard?.figureName)
    }

    @Test
    fun quoteCardIsNullWhenNoQuotesSaved() = runTest(testDispatcher) {
        val viewModel = readerViewModel(figure = testFigure, latestQuote = null)

        val state = viewModel.state.value as ReaderContract.UiState.Ready
        assertNull(state.quoteCard)
    }

    @Test
    fun daySlotTapped_opensWeekSlotPickerForThatDay() = runTest(testDispatcher) {
        val viewModel = readerViewModel(figure = testFigure, latestQuote = null)

        viewModel.onIntent(ReaderContract.Intent.DaySlotTapped(index = 2))

        val state = viewModel.state.value as ReaderContract.UiState.Ready
        val sheet = state.activeSheet as? ReaderContract.ActiveSheet.WeekSlotPicker
        assertNotNull(sheet)
        assertEquals(2, sheet.dayOfWeek)
    }

    @Test
    fun pickerDismissed_clearsActiveSheet() = runTest(testDispatcher) {
        val viewModel = readerViewModel(figure = testFigure, latestQuote = null)
        viewModel.onIntent(ReaderContract.Intent.DaySlotTapped(index = 0))

        viewModel.onIntent(ReaderContract.Intent.PickerDismissed)

        val state = viewModel.state.value as ReaderContract.UiState.Ready
        assertNull(state.activeSheet)
    }

    @Test
    fun figureAssigned_appliesImmediatelyWhenTodayHasNoBriefingYet() = runTest(testDispatcher) {
        val otherFigure = Figure(id = 2L, name = "C.S. Lewis", category = FigureCategory.THEOLOGIAN, century = "20th", role = "Author")
        val (viewModel, dayAssignmentRepo) = readerViewModelWithRepo(figure = testFigure, extraFigures = listOf(otherFigure))

        viewModel.onIntent(ReaderContract.Intent.FigureAssigned(dayOfWeek = todayOrdinal, figureId = 2L, lens = null))

        assertEquals(listOf(Triple(todayOrdinal, 2L, null as LensFilter?)), dayAssignmentRepo.assignCalls)
        val state = viewModel.state.value as ReaderContract.UiState.Ready
        assertNull(state.pendingReassignment)
    }

    @Test
    fun figureAssigned_promptsConfirmationWhenTodayAlreadyBriefedForADifferentFigure() = runTest(testDispatcher) {
        val otherFigure = Figure(id = 2L, name = "C.S. Lewis", category = FigureCategory.THEOLOGIAN, century = "20th", role = "Author")
        val briefing = BriefingDay(epochDay = todayEpochDay, figureId = 1L, scriptureReference = "John 3:16", scriptureText = "…")
        val (viewModel, dayAssignmentRepo) = readerViewModelWithRepo(
            figure = testFigure,
            extraFigures = listOf(otherFigure),
            briefings = listOf(briefing),
        )

        viewModel.onIntent(ReaderContract.Intent.FigureAssigned(dayOfWeek = todayOrdinal, figureId = 2L, lens = null))

        assertTrue(dayAssignmentRepo.assignCalls.isEmpty())
        val state = viewModel.state.value as ReaderContract.UiState.Ready
        val pending = state.pendingReassignment
        assertNotNull(pending)
        assertEquals("Augustine of Hippo", pending.currentFigureName)
        assertEquals("C.S. Lewis", pending.newFigureName)
    }

    @Test
    fun confirmReassignment_appliesTheWriteAndClearsTheDialog() = runTest(testDispatcher) {
        val otherFigure = Figure(id = 2L, name = "C.S. Lewis", category = FigureCategory.THEOLOGIAN, century = "20th", role = "Author")
        val briefing = BriefingDay(epochDay = todayEpochDay, figureId = 1L, scriptureReference = "John 3:16", scriptureText = "…")
        val (viewModel, dayAssignmentRepo) = readerViewModelWithRepo(
            figure = testFigure,
            extraFigures = listOf(otherFigure),
            briefings = listOf(briefing),
        )
        viewModel.onIntent(ReaderContract.Intent.FigureAssigned(dayOfWeek = todayOrdinal, figureId = 2L, lens = null))

        viewModel.onIntent(ReaderContract.Intent.ConfirmReassignment)

        assertEquals(listOf(Triple(todayOrdinal, 2L, null as LensFilter?)), dayAssignmentRepo.assignCalls)
        val state = viewModel.state.value as ReaderContract.UiState.Ready
        assertNull(state.pendingReassignment)
    }

    @Test
    fun userDisplayNameIsPopulatedFromTheSignedInSession() = runTest(testDispatcher) {
        val (viewModel, _) = readerViewModelWithRepo(figure = testFigure, session = UserSession("u1", "a@b.com", "Jordan"))

        val state = viewModel.state.value as ReaderContract.UiState.Ready
        assertEquals("Jordan", state.userDisplayName)
    }

    @Test
    fun userDisplayNameIsNullWhenSessionHasNoDisplayNameSet() = runTest(testDispatcher) {
        val (viewModel, _) = readerViewModelWithRepo(figure = testFigure, session = UserSession("u1", "a@b.com", null))

        val state = viewModel.state.value as ReaderContract.UiState.Ready
        assertNull(state.userDisplayName)
    }

    @Test
    fun cancelReassignment_leavesAssignmentUnchanged() = runTest(testDispatcher) {
        val otherFigure = Figure(id = 2L, name = "C.S. Lewis", category = FigureCategory.THEOLOGIAN, century = "20th", role = "Author")
        val briefing = BriefingDay(epochDay = todayEpochDay, figureId = 1L, scriptureReference = "John 3:16", scriptureText = "…")
        val (viewModel, dayAssignmentRepo) = readerViewModelWithRepo(
            figure = testFigure,
            extraFigures = listOf(otherFigure),
            briefings = listOf(briefing),
        )
        viewModel.onIntent(ReaderContract.Intent.FigureAssigned(dayOfWeek = todayOrdinal, figureId = 2L, lens = null))

        viewModel.onIntent(ReaderContract.Intent.CancelReassignment)

        assertTrue(dayAssignmentRepo.assignCalls.isEmpty())
        val state = viewModel.state.value as ReaderContract.UiState.Ready
        assertNull(state.pendingReassignment)
    }

    @Test
    fun pastBriefingsShowsMostRecentReflectionsFirstAndExcludesToday() = runTest(testDispatcher) {
        val todaysBriefing = BriefingDay(epochDay = todayEpochDay, figureId = 1L, inspiration = "Today's word")
        val yesterday = BriefingDay(epochDay = todayEpochDay - 1, figureId = 1L, inspiration = "Yesterday's word")
        val twoDaysAgo = BriefingDay(epochDay = todayEpochDay - 2, figureId = 1L, inspiration = "Older word")
        val (viewModel, _) = readerViewModelWithRepo(
            figure = testFigure,
            briefings = listOf(todaysBriefing, yesterday, twoDaysAgo),
        )

        val state = viewModel.state.value as ReaderContract.UiState.Ready

        assertEquals(2, state.pastBriefings.size)
        assertEquals(todayEpochDay - 1, state.pastBriefings.first().epochDay)
        assertEquals("Yesterday's word", state.pastBriefings.first().inspiration)
    }

    @Test
    fun pastBriefingsLabelsOneDayAgoAsYesterday() = runTest(testDispatcher) {
        val yesterday = BriefingDay(epochDay = todayEpochDay - 1, figureId = 1L, inspiration = "Yesterday's word")
        val (viewModel, _) = readerViewModelWithRepo(figure = testFigure, briefings = listOf(yesterday))

        val state = viewModel.state.value as ReaderContract.UiState.Ready

        assertEquals(ReaderContract.DayLabel.Yesterday, state.pastBriefings.first().dayLabel)
    }

    @Test
    fun pastBriefingsLabelsTwoToSixDaysAgoWithTheWeekdayName() = runTest(testDispatcher) {
        val threeDaysAgo = BriefingDay(epochDay = todayEpochDay - 3, figureId = 1L, inspiration = "Word")
        val (viewModel, _) = readerViewModelWithRepo(figure = testFigure, briefings = listOf(threeDaysAgo))

        val state = viewModel.state.value as ReaderContract.UiState.Ready

        val expectedWeekday = LocalDate.fromEpochDays((todayEpochDay - 3).toInt()).dayOfWeek.name
            .lowercase().replaceFirstChar { it.uppercase() }
        assertEquals(ReaderContract.DayLabel.Text(expectedWeekday), state.pastBriefings.first().dayLabel)
    }

    @Test
    fun pastBriefingsLabelsSevenOrMoreDaysAgoWithAFullDate() = runTest(testDispatcher) {
        val epochDay = todayEpochDay - 8
        val eightDaysAgo = BriefingDay(epochDay = epochDay, figureId = 1L, inspiration = "Word")
        val (viewModel, _) = readerViewModelWithRepo(figure = testFigure, briefings = listOf(eightDaysAgo))

        val state = viewModel.state.value as ReaderContract.UiState.Ready

        val date = LocalDate.fromEpochDays(epochDay.toInt())
        val monthName = date.month.name.lowercase().replaceFirstChar { it.uppercase() }
        val expected = "$monthName ${date.dayOfMonth}, ${date.year}"
        assertEquals(ReaderContract.DayLabel.Text(expected), state.pastBriefings.first().dayLabel)
    }

    @Test
    fun pastBriefingsCapAtSevenMostRecentDays() = runTest(testDispatcher) {
        val briefings = (1..10).map { daysAgo ->
            BriefingDay(epochDay = todayEpochDay - daysAgo, figureId = 1L, inspiration = "Word $daysAgo")
        }
        val (viewModel, _) = readerViewModelWithRepo(figure = testFigure, briefings = briefings)

        val state = viewModel.state.value as ReaderContract.UiState.Ready

        assertEquals(7, state.pastBriefings.size)
        assertEquals(todayEpochDay - 1, state.pastBriefings.first().epochDay)
        assertEquals(todayEpochDay - 7, state.pastBriefings.last().epochDay)
        assertTrue(state.hasMorePastBriefings)
    }

    @Test
    fun pastBriefingsIsEmptyWhenNoPastReflectionsExist() = runTest(testDispatcher) {
        val (viewModel, _) = readerViewModelWithRepo(figure = testFigure, briefings = emptyList())

        val state = viewModel.state.value as ReaderContract.UiState.Ready

        assertTrue(state.pastBriefings.isEmpty())
        assertFalse(state.hasMorePastBriefings)
    }

    @Test
    fun hasMorePastBriefingsIsFalseWhenCountIsAtOrBelowTheCap() = runTest(testDispatcher) {
        val briefings = (1..7).map { daysAgo ->
            BriefingDay(epochDay = todayEpochDay - daysAgo, figureId = 1L, inspiration = "Word $daysAgo")
        }
        val (viewModel, _) = readerViewModelWithRepo(figure = testFigure, briefings = briefings)

        val state = viewModel.state.value as ReaderContract.UiState.Ready

        assertEquals(7, state.pastBriefings.size)
        assertFalse(state.hasMorePastBriefings)
    }

    /**
     * Builds the ViewModel and starts collecting its state. `stateIn(WhileSubscribed)` is cold
     * until a subscriber is present, so an active collector in [backgroundScope] is required for
     * `state.value` to reflect the pipeline output.
     */
    private fun TestScope.readerViewModel(
        figure: Figure,
        latestQuote: Quote?,
        extraFigures: List<Figure> = emptyList(),
        assignments: Map<Int, DayAssignment> = emptyMap(),
    ): ReaderViewModel = readerViewModelWithRepo(figure, extraFigures, assignments, emptyList(), latestQuote).first

    private fun TestScope.readerViewModelWithRepo(
        figure: Figure,
        extraFigures: List<Figure> = emptyList(),
        assignments: Map<Int, DayAssignment> = emptyMap(),
        briefings: List<BriefingDay> = emptyList(),
        latestQuote: Quote? = null,
        session: UserSession? = null,
    ): Pair<ReaderViewModel, FakeDayAssignmentRepository> {
        val figureRepo = FakeFigureRepository(listOf(figure) + extraFigures)
        val dayAssignmentRepo = FakeDayAssignmentRepository(MutableStateFlow(assignments))
        val quoteRepo = FakeQuoteRepository(latestQuote)
        val reflectionRepo = FakeDailyReflectionRepository(briefings)
        val authRepo = FakeAuthRepository(session)
        val viewModel = ReaderViewModel(
            getReaderCalendar = GetReaderCalendarUseCase(figureRepo, dayAssignmentRepo, quoteRepo, reflectionRepo),
            dayAssignmentRepository = dayAssignmentRepo,
            authRepository = authRepo,
            reflectionRepository = reflectionRepo,
        )
        backgroundScope.launch(testDispatcher) { viewModel.state.collect {} }
        return viewModel to dayAssignmentRepo
    }

    private companion object {
        val today = Instant.fromEpochMilliseconds(epochMillis()).toLocalDateTime(TimeZone.currentSystemDefault()).date
        val todayOrdinal = today.dayOfWeek.ordinal
        val todayEpochDay = today.toEpochDays().toLong()
    }
}

private class FakeFigureRepository(private val figures: List<Figure>) : FigureRepository {
    private val flow = MutableStateFlow(figures)
    override fun observeAllFigures(): Flow<List<Figure>> = flow
    override fun observeFiguresByCategory(category: FigureCategory): Flow<List<Figure>> = MutableStateFlow(emptyList())
    override suspend fun getFigureById(id: Long): Figure? = figures.firstOrNull { it.id == id }
    override suspend fun getFigureByName(name: String): Figure? = figures.firstOrNull { it.name == name }
    override suspend fun syncFigures() = Unit
}

private class FakeDayAssignmentRepository(
    private val assignmentsFlow: MutableStateFlow<Map<Int, DayAssignment>>
) : DayAssignmentRepository {
    val assignCalls = mutableListOf<Triple<Int, Long, LensFilter?>>()
    val clearCalls = mutableListOf<Int>()
    override fun observeAssignments(): Flow<Map<Int, DayAssignment>> = assignmentsFlow
    override suspend fun assign(dayOfWeek: Int, figureId: Long, lens: LensFilter?) {
        assignCalls.add(Triple(dayOfWeek, figureId, lens))
    }
    override suspend fun clear(dayOfWeek: Int) {
        clearCalls.add(dayOfWeek)
    }
    override val isResolved: StateFlow<Boolean> = MutableStateFlow(true)
    override suspend fun resolveReporter(epochDay: Long, dayOfWeek: Int): Long? = null
    override suspend fun resolve(userId: String?) = Unit
}

private class FakeDailyReflectionRepository(
    private val briefings: List<BriefingDay> = emptyList(),
) : DailyReflectionRepository {
    override suspend fun getOrFetch(
        figureId: Long,
        figureName: String,
        headlines: List<String>,
        tone: String,
        theme: String?,
    ): DailyReflection = throw UnsupportedOperationException()
    override fun observeByEpochDayRange(startEpochDay: Long, endEpochDay: Long): Flow<List<BriefingDay>> =
        MutableStateFlow(briefings.filter { it.epochDay in startEpochDay..endEpochDay })
    override suspend fun getForDay(epochDay: Long, tone: String): DailyReflection? = null
    override suspend fun getEarliestBriefingEpochDay(): Long? = briefings.minOfOrNull { it.epochDay }
    override suspend fun getLockedFigureId(epochDay: Long): Long? =
        briefings.firstOrNull { it.epochDay == epochDay }?.figureId
    override val isResolved: StateFlow<Boolean> = MutableStateFlow(true)
    override suspend fun resolve(userId: String?) = Unit
}

private class FakeAuthRepository(private val session: UserSession?) : AuthRepository {
    override fun observeAuthState(): Flow<UserSession?> = MutableStateFlow(session)
    override fun currentSession(): UserSession? = session
    override suspend fun signInWithEmail(email: String, password: String) = Unit
    override suspend fun signUp(email: String, password: String, displayName: String) = Unit
    override suspend fun verifySignUpOtp(email: String, token: String) = Unit
    override suspend fun signOut() = Unit
}

private class FakeQuoteRepository(private val latestQuote: Quote?) : QuoteRepository {
    override fun observeAllQuotes(): Flow<List<Quote>> = MutableStateFlow(listOfNotNull(latestQuote))
    override fun observeQuotesByFigure(figureId: Long): Flow<List<Quote>> = MutableStateFlow(listOfNotNull(latestQuote))
    override suspend fun getQuoteById(id: Long): Quote? = latestQuote?.takeIf { it.id == id }
    override suspend fun getLatestQuoteForFigure(figureId: Long): Quote? = latestQuote
    override suspend fun saveQuote(text: String, source: String, themes: List<String>, figureId: Long) = Unit
    override fun observeMemorizedQuote(): Flow<Quote?> = MutableStateFlow(latestQuote)
    override suspend fun memorizeQuote(figureId: Long, text: String) = Unit
    override val isResolved: StateFlow<Boolean> = MutableStateFlow(true)
    override suspend fun resolve(userId: String?) = Unit
}
