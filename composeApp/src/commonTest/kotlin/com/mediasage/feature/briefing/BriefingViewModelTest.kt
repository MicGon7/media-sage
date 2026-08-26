@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.mediasage.feature.briefing

import com.mediasage.data.repository.epochMillis
import com.mediasage.domain.model.BriefingDay
import com.mediasage.domain.model.DailyReflection
import com.mediasage.domain.model.DayAssignment
import com.mediasage.domain.model.Figure
import com.mediasage.domain.model.FigureCategory
import com.mediasage.domain.model.Headline
import com.mediasage.domain.model.LensFilter
import com.mediasage.domain.repository.DailyReflectionRepository
import com.mediasage.domain.repository.DayAssignmentRepository
import com.mediasage.domain.repository.FigureRepository
import com.mediasage.domain.repository.HeadlineRepository
import com.mediasage.domain.repository.UserReflectionNoteRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.time.Instant

class BriefingViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    private val judson = Figure(id = 1L, name = "Adoniram Judson", category = FigureCategory.MISSIONARY, century = "19th", role = "Missionary to Burma")
    private val lincoln = Figure(id = 2L, name = "Abraham Lincoln", category = FigureCategory.INTELLECTUAL, century = "19th", role = "President")

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun loadCard_keepsLockedFigureEvenWhenAssignmentPointsToADifferentFigure() = runTest(testDispatcher) {
        // Regression test for the MS-658 bug: confirming a reassignment must never swap today's
        // already-displayed briefing, even though the weekday assignment row now points elsewhere.
        val viewModel = briefingViewModel(
            figures = listOf(judson, lincoln),
            assignments = mapOf(todayOrdinal to DayAssignment(figureId = 2L, lens = null)),
            resolveReporterResult = 1L,
        )

        val state = viewModel.state.value as BriefingContract.UiState.Success
        val card = state.card as BriefingContract.CardState.Ready
        assertEquals(1L, card.figureId)
        assertEquals("Adoniram Judson", card.figureName)
    }

    @Test
    fun loadCard_usesCurrentAssignmentWhenTodayIsNotLocked() = runTest(testDispatcher) {
        val viewModel = briefingViewModel(
            figures = listOf(judson, lincoln),
            assignments = mapOf(todayOrdinal to DayAssignment(figureId = 2L, lens = null)),
            resolveReporterResult = null,
        )

        val state = viewModel.state.value as BriefingContract.UiState.Success
        val card = state.card as BriefingContract.CardState.Ready
        assertEquals(2L, card.figureId)
        assertEquals("Abraham Lincoln", card.figureName)
    }

    @Test
    fun loadCard_fallsBackToFirstFigureWhenNoAssignmentAndNotLocked() = runTest(testDispatcher) {
        val viewModel = briefingViewModel(
            figures = listOf(judson, lincoln),
            assignments = emptyMap(),
            resolveReporterResult = null,
        )

        val state = viewModel.state.value as BriefingContract.UiState.Success
        val card = state.card as BriefingContract.CardState.Ready
        assertEquals(1L, card.figureId)
    }

    @Test
    fun loadCard_rapidAssignmentChangesDuringSyncNeverSurfaceCancellationAsError() = runTest(testDispatcher) {
        // Regression test: on a fresh install, day-assignment sync can upsert several rows in
        // quick succession, re-triggering collectLatest and cancelling the in-flight getOrFetch
        // call each time. That cancellation must never be surfaced as a ShowError side effect,
        // and the final, settled emission must still complete and render a Ready card.
        val assignmentsFlow = MutableStateFlow(mapOf(todayOrdinal to DayAssignment(figureId = 1L, lens = null)))
        val reflectionRepo = FakeDailyReflectionRepository(fetchDelayMs = 50)
        val viewModel = BriefingViewModel(
            dayAssignmentRepository = FakeDayAssignmentRepository(assignmentsFlow, resolveReporterResult = null),
            dailyReflectionRepository = reflectionRepo,
            figureRepository = FakeFigureRepository(listOf(judson, lincoln)),
            headlineRepository = FakeHeadlineRepository(),
            userReflectionNoteRepository = FakeUserReflectionNoteRepository(),
            toneScheduler = FakeBriefingToneScheduler(),
        )
        val sideEffects = mutableListOf<BriefingContract.SideEffect>()
        backgroundScope.launch(testDispatcher) { viewModel.sideEffects.collect { sideEffects.add(it) } }
        backgroundScope.launch(testDispatcher) { viewModel.state.collect {} }

        repeat(5) {
            advanceTimeBy(1)
            assignmentsFlow.value = mapOf(todayOrdinal to DayAssignment(figureId = 1L, lens = null))
        }
        advanceUntilIdle()

        assertEquals(emptyList(), sideEffects.filterIsInstance<BriefingContract.SideEffect.ShowError>())
        val state = viewModel.state.value as BriefingContract.UiState.Success
        assertIs<BriefingContract.CardState.Ready>(state.card)
    }

    @Test
    fun loadCard_waitsForDayAssignmentResolutionBeforeResolvingReporter() = runTest(testDispatcher) {
        // Regression test for MS-663: on a cold start, day-assignment resolution can still be
        // in flight when BriefingViewModel first collects. It must wait for the completion
        // signal rather than resolving against an empty/mid-flight table and locking in the
        // first-in-list figure as a fallback.
        val isResolved = MutableStateFlow(false)
        val viewModel = BriefingViewModel(
            dayAssignmentRepository = FakeDayAssignmentRepository(
                assignmentsFlow = MutableStateFlow(mapOf(todayOrdinal to DayAssignment(figureId = 2L, lens = null))),
                resolveReporterResult = null,
                isResolved = isResolved,
            ),
            dailyReflectionRepository = FakeDailyReflectionRepository(),
            figureRepository = FakeFigureRepository(listOf(judson, lincoln)),
            headlineRepository = FakeHeadlineRepository(),
            userReflectionNoteRepository = FakeUserReflectionNoteRepository(),
            toneScheduler = FakeBriefingToneScheduler(),
        )
        backgroundScope.launch(testDispatcher) { viewModel.state.collect {} }
        advanceUntilIdle()

        val loadingState = viewModel.state.value as BriefingContract.UiState.Success
        assertIs<BriefingContract.CardState.Loading>(loadingState.card)

        isResolved.value = true
        advanceUntilIdle()

        val state = viewModel.state.value as BriefingContract.UiState.Success
        val card = state.card as BriefingContract.CardState.Ready
        assertEquals(2L, card.figureId)
    }

    @Test
    fun loadCard_waitsForDailyReflectionResolutionBeforeResolvingReporter() = runTest(testDispatcher) {
        // Regression test for MS-664: on a cold start, reflection sync can still be in flight
        // when BriefingViewModel first collects. It must wait for that completion signal too,
        // or a second device could read an empty local table and generate its own, different
        // reflection for a day another device already briefed.
        val isResolved = MutableStateFlow(false)
        val viewModel = BriefingViewModel(
            dayAssignmentRepository = FakeDayAssignmentRepository(
                assignmentsFlow = MutableStateFlow(mapOf(todayOrdinal to DayAssignment(figureId = 2L, lens = null))),
                resolveReporterResult = null,
            ),
            dailyReflectionRepository = FakeDailyReflectionRepository(isResolved = isResolved),
            figureRepository = FakeFigureRepository(listOf(judson, lincoln)),
            headlineRepository = FakeHeadlineRepository(),
            userReflectionNoteRepository = FakeUserReflectionNoteRepository(),
            toneScheduler = FakeBriefingToneScheduler(),
        )
        backgroundScope.launch(testDispatcher) { viewModel.state.collect {} }
        advanceUntilIdle()

        val loadingState = viewModel.state.value as BriefingContract.UiState.Success
        assertIs<BriefingContract.CardState.Loading>(loadingState.card)

        isResolved.value = true
        advanceUntilIdle()

        val state = viewModel.state.value as BriefingContract.UiState.Success
        val card = state.card as BriefingContract.CardState.Ready
        assertEquals(2L, card.figureId)
    }

    @Test
    fun loadCard_showsLoadingAgainInsteadOfStaleDataWhenResolutionRestartsMidSession() = runTest(testDispatcher) {
        // Regression test: on a fresh install, resolve(null) can seed fallback defaults and flip
        // isResolved true before the real signed-in resolve(userId) corrects that data moments
        // later. isResolved must be a live signal, not a one-time gate — otherwise the fallback
        // figure flashes visibly before silently being swapped for the real one.
        val assignmentsFlow = MutableStateFlow(mapOf(todayOrdinal to DayAssignment(figureId = 1L, lens = null)))
        val isResolved = MutableStateFlow(true)
        val viewModel = BriefingViewModel(
            dayAssignmentRepository = FakeDayAssignmentRepository(
                assignmentsFlow = assignmentsFlow,
                resolveReporterResult = null,
                isResolved = isResolved,
            ),
            dailyReflectionRepository = FakeDailyReflectionRepository(),
            figureRepository = FakeFigureRepository(listOf(judson, lincoln)),
            headlineRepository = FakeHeadlineRepository(),
            userReflectionNoteRepository = FakeUserReflectionNoteRepository(),
            toneScheduler = FakeBriefingToneScheduler(),
        )
        backgroundScope.launch(testDispatcher) { viewModel.state.collect {} }
        advanceUntilIdle()

        val fallbackState = viewModel.state.value as BriefingContract.UiState.Success
        assertEquals(1L, (fallbackState.card as BriefingContract.CardState.Ready).figureId)

        // The real sign-in's resolve() begins correcting the seeded fallback data.
        isResolved.value = false
        advanceUntilIdle()
        val midResync = viewModel.state.value as BriefingContract.UiState.Success
        assertIs<BriefingContract.CardState.Loading>(midResync.card)

        assignmentsFlow.value = mapOf(todayOrdinal to DayAssignment(figureId = 2L, lens = null))
        isResolved.value = true
        advanceUntilIdle()

        val resolvedState = viewModel.state.value as BriefingContract.UiState.Success
        assertEquals(2L, (resolvedState.card as BriefingContract.CardState.Ready).figureId)
    }

    @Test
    fun loadCard_reloadsWhenToneBoundaryScheduleFires() = runTest(testDispatcher) {
        // A screen left open across the 5pm/midnight tone boundary must refresh on its own.
        // BriefingToneScheduler is the injected wake-up signal for that transition instant —
        // firing it should trigger exactly one more reflection fetch, with no navigation or retry.
        val toneScheduler = FakeBriefingToneScheduler()
        val reflectionRepo = FakeDailyReflectionRepository()
        val viewModel = BriefingViewModel(
            dayAssignmentRepository = FakeDayAssignmentRepository(
                MutableStateFlow(mapOf(todayOrdinal to DayAssignment(figureId = 1L, lens = null))),
                resolveReporterResult = null,
            ),
            dailyReflectionRepository = reflectionRepo,
            figureRepository = FakeFigureRepository(listOf(judson, lincoln)),
            headlineRepository = FakeHeadlineRepository(),
            userReflectionNoteRepository = FakeUserReflectionNoteRepository(),
            toneScheduler = toneScheduler,
        )
        backgroundScope.launch(testDispatcher) { viewModel.state.collect {} }
        advanceUntilIdle()
        assertEquals(1, reflectionRepo.fetchCount)

        toneScheduler.crossBoundary()
        advanceUntilIdle()

        assertEquals(2, reflectionRepo.fetchCount)
        val state = viewModel.state.value as BriefingContract.UiState.Success
        assertIs<BriefingContract.CardState.Ready>(state.card)
    }

    @Test
    fun loadCard_filtersNewsLensHeadlinesToTheFiveNonExcludedCategories() = runTest(testDispatcher) {
        // AC: the NEWS lens must draw from world/nation/business/science/health only —
        // general and technology are fetched/tagged server-side but excluded from the briefing.
        val reflectionRepo = FakeDailyReflectionRepository()
        val headlines = listOf(
            Headline(id = 1L, title = "World story", source = "src", url = "u1", imageUrl = null, publishedAt = 0, fetchedAt = 0, category = "world"),
            Headline(id = 2L, title = "General story", source = "src", url = "u2", imageUrl = null, publishedAt = 0, fetchedAt = 0, category = "general"),
            Headline(id = 3L, title = "Tech story", source = "src", url = "u3", imageUrl = null, publishedAt = 0, fetchedAt = 0, category = "technology"),
            Headline(id = 4L, title = "Health story", source = "src", url = "u4", imageUrl = null, publishedAt = 0, fetchedAt = 0, category = "health"),
        )
        val viewModel = BriefingViewModel(
            dayAssignmentRepository = FakeDayAssignmentRepository(
                MutableStateFlow(mapOf(todayOrdinal to DayAssignment(figureId = 1L, lens = LensFilter.NEWS))),
                resolveReporterResult = 1L,
            ),
            dailyReflectionRepository = reflectionRepo,
            figureRepository = FakeFigureRepository(listOf(judson)),
            headlineRepository = FakeHeadlineRepository(headlines),
            userReflectionNoteRepository = FakeUserReflectionNoteRepository(),
            toneScheduler = FakeBriefingToneScheduler(),
        )
        backgroundScope.launch(testDispatcher) { viewModel.state.collect {} }
        advanceUntilIdle()

        assertEquals(listOf("World story", "Health story"), reflectionRepo.lastHeadlines)
    }

    @Test
    fun reflectTapped_opensSheetWithChallengeAndSavedNote() = runTest(testDispatcher) {
        val noteRepo = FakeUserReflectionNoteRepository()
        val viewModel = BriefingViewModel(
            dayAssignmentRepository = FakeDayAssignmentRepository(MutableStateFlow(emptyMap()), resolveReporterResult = 1L),
            dailyReflectionRepository = FakeDailyReflectionRepository(challenge = "What is one way to show love today?"),
            figureRepository = FakeFigureRepository(listOf(judson)),
            headlineRepository = FakeHeadlineRepository(),
            userReflectionNoteRepository = noteRepo,
            toneScheduler = FakeBriefingToneScheduler(),
        )
        backgroundScope.launch(testDispatcher) { viewModel.state.collect {} }
        advanceUntilIdle()

        viewModel.onIntent(BriefingContract.Intent.ReflectTapped)
        advanceUntilIdle()

        val state = viewModel.state.value as BriefingContract.UiState.Success
        val sheet = requireNotNull(state.reflectSheet)
        assertEquals("What is one way to show love today?", sheet.challenge)
        assertEquals("", sheet.noteText)
    }

    @Test
    fun reflectNoteSaved_persistsNoteAndUpdatesSavedText() = runTest(testDispatcher) {
        val noteRepo = FakeUserReflectionNoteRepository()
        val viewModel = BriefingViewModel(
            dayAssignmentRepository = FakeDayAssignmentRepository(MutableStateFlow(emptyMap()), resolveReporterResult = 1L),
            dailyReflectionRepository = FakeDailyReflectionRepository(challenge = "What is one way to show love today?"),
            figureRepository = FakeFigureRepository(listOf(judson)),
            headlineRepository = FakeHeadlineRepository(),
            userReflectionNoteRepository = noteRepo,
            toneScheduler = FakeBriefingToneScheduler(),
        )
        backgroundScope.launch(testDispatcher) { viewModel.state.collect {} }
        advanceUntilIdle()

        viewModel.onIntent(BriefingContract.Intent.ReflectTapped)
        advanceUntilIdle()
        viewModel.onIntent(BriefingContract.Intent.ReflectNoteChanged("Called my neighbor."))
        viewModel.onIntent(BriefingContract.Intent.ReflectNoteSaved)
        advanceUntilIdle()

        val state = viewModel.state.value as BriefingContract.UiState.Success
        val sheet = requireNotNull(state.reflectSheet)
        assertEquals("Called my neighbor.", sheet.savedNoteText)
        assertEquals(1, noteRepo.savedNotes.size)
    }

    @Test
    fun reflectNoteChanged_capsAtMaxLength() = runTest(testDispatcher) {
        val viewModel = BriefingViewModel(
            dayAssignmentRepository = FakeDayAssignmentRepository(MutableStateFlow(emptyMap()), resolveReporterResult = 1L),
            dailyReflectionRepository = FakeDailyReflectionRepository(challenge = "What is one way to show love today?"),
            figureRepository = FakeFigureRepository(listOf(judson)),
            headlineRepository = FakeHeadlineRepository(),
            userReflectionNoteRepository = FakeUserReflectionNoteRepository(),
            toneScheduler = FakeBriefingToneScheduler(),
        )
        backgroundScope.launch(testDispatcher) { viewModel.state.collect {} }
        advanceUntilIdle()

        viewModel.onIntent(BriefingContract.Intent.ReflectTapped)
        advanceUntilIdle()
        viewModel.onIntent(BriefingContract.Intent.ReflectNoteChanged("a".repeat(5_000)))

        val state = viewModel.state.value as BriefingContract.UiState.Success
        val sheet = requireNotNull(state.reflectSheet)
        assertEquals(4_000, sheet.noteText.length)
    }

    @Test
    fun reflectDismissed_closesSheet() = runTest(testDispatcher) {
        val viewModel = BriefingViewModel(
            dayAssignmentRepository = FakeDayAssignmentRepository(MutableStateFlow(emptyMap()), resolveReporterResult = 1L),
            dailyReflectionRepository = FakeDailyReflectionRepository(challenge = "What is one way to show love today?"),
            figureRepository = FakeFigureRepository(listOf(judson)),
            headlineRepository = FakeHeadlineRepository(),
            userReflectionNoteRepository = FakeUserReflectionNoteRepository(),
            toneScheduler = FakeBriefingToneScheduler(),
        )
        backgroundScope.launch(testDispatcher) { viewModel.state.collect {} }
        advanceUntilIdle()

        viewModel.onIntent(BriefingContract.Intent.ReflectTapped)
        advanceUntilIdle()
        viewModel.onIntent(BriefingContract.Intent.ReflectDismissed)

        val state = viewModel.state.value as BriefingContract.UiState.Success
        assertEquals(null, state.reflectSheet)
    }

    @Test
    fun reflectTapped_doesNothingWhenNoChallengeOnReflection() = runTest(testDispatcher) {
        val viewModel = BriefingViewModel(
            dayAssignmentRepository = FakeDayAssignmentRepository(MutableStateFlow(emptyMap()), resolveReporterResult = 1L),
            dailyReflectionRepository = FakeDailyReflectionRepository(),
            figureRepository = FakeFigureRepository(listOf(judson)),
            headlineRepository = FakeHeadlineRepository(),
            userReflectionNoteRepository = FakeUserReflectionNoteRepository(),
            toneScheduler = FakeBriefingToneScheduler(),
        )
        backgroundScope.launch(testDispatcher) { viewModel.state.collect {} }
        advanceUntilIdle()

        viewModel.onIntent(BriefingContract.Intent.ReflectTapped)
        advanceUntilIdle()

        val state = viewModel.state.value as BriefingContract.UiState.Success
        assertEquals(null, state.reflectSheet)
    }

    private fun TestScope.briefingViewModel(
        figures: List<Figure>,
        assignments: Map<Int, DayAssignment>,
        resolveReporterResult: Long?,
    ): BriefingViewModel {
        val viewModel = BriefingViewModel(
            dayAssignmentRepository = FakeDayAssignmentRepository(MutableStateFlow(assignments), resolveReporterResult),
            dailyReflectionRepository = FakeDailyReflectionRepository(),
            figureRepository = FakeFigureRepository(figures),
            headlineRepository = FakeHeadlineRepository(),
            userReflectionNoteRepository = FakeUserReflectionNoteRepository(),
            toneScheduler = FakeBriefingToneScheduler(),
        )
        backgroundScope.launch(testDispatcher) { viewModel.state.collect {} }
        return viewModel
    }

    private companion object {
        val todayOrdinal = Instant.fromEpochMilliseconds(epochMillis())
            .toLocalDateTime(TimeZone.currentSystemDefault()).date.dayOfWeek.ordinal
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
    private val assignmentsFlow: MutableStateFlow<Map<Int, DayAssignment>>,
    private val resolveReporterResult: Long?,
    override val isResolved: StateFlow<Boolean> = MutableStateFlow(true),
) : DayAssignmentRepository {
    override fun observeAssignments(): Flow<Map<Int, DayAssignment>> = assignmentsFlow
    override suspend fun assign(dayOfWeek: Int, figureId: Long, lens: LensFilter?) = Unit
    override suspend fun clear(dayOfWeek: Int) = Unit
    override suspend fun resolveReporter(epochDay: Long, dayOfWeek: Int): Long? =
        resolveReporterResult ?: assignmentsFlow.value[dayOfWeek]?.figureId
    override suspend fun resolve(userId: String?) = Unit
}

private class FakeDailyReflectionRepository(
    private val fetchDelayMs: Long = 0,
    private val challenge: String? = null,
    override val isResolved: StateFlow<Boolean> = MutableStateFlow(true),
) : DailyReflectionRepository {
    var fetchCount = 0
        private set
    var lastHeadlines: List<String> = emptyList()
        private set

    override suspend fun getOrFetch(
        figureId: Long,
        figureName: String,
        headlines: List<String>,
        tone: String,
        theme: String?,
    ): DailyReflection {
        fetchCount++
        lastHeadlines = headlines
        if (fetchDelayMs > 0) delay(fetchDelayMs)
        return DailyReflection(
            scriptureReference = "John 3:16",
            scriptureText = "For God so loved the world",
            insight = "insight",
            implication = "implication",
            inspiration = "inspiration",
            sources = emptyList(),
            tone = tone,
            theme = theme,
            challenge = challenge,
        )
    }
    override fun observeByEpochDayRange(startEpochDay: Long, endEpochDay: Long): Flow<List<BriefingDay>> =
        MutableStateFlow(emptyList())
    override suspend fun getForDay(epochDay: Long, tone: String): DailyReflection? = null
    override suspend fun getEarliestBriefingEpochDay(): Long? = null
    override suspend fun getLockedFigureId(epochDay: Long): Long? = null
    override suspend fun resolve(userId: String?) = Unit
}

private class FakeHeadlineRepository(private val headlines: List<Headline> = emptyList()) : HeadlineRepository {
    override fun observeHeadlines(): Flow<List<Headline>> = MutableStateFlow(headlines)
    override suspend fun getHeadlineById(id: Long): Headline? = null
    override suspend fun getHeadlineByUrl(url: String): Headline? = null
    override suspend fun refreshHeadlines() = Unit
    override suspend fun clearOldHeadlines(olderThanMillis: Long) = Unit
    override suspend fun markAsRead(url: String) = Unit
}

/** Never fires unless [crossBoundary] is called — avoids a real multi-hour delay in tests. */
private class FakeBriefingToneScheduler : BriefingToneScheduler {
    private val boundaryCrossed = Channel<Unit>(Channel.BUFFERED)
    override suspend fun awaitNextToneBoundary() {
        boundaryCrossed.receive()
    }
    suspend fun crossBoundary() = boundaryCrossed.send(Unit)
}

private class FakeUserReflectionNoteRepository : UserReflectionNoteRepository {
    private val notes = mutableMapOf<String, String>()
    val savedNotes: Map<String, String> get() = notes
    override suspend fun getNote(reflectionId: String): String? = notes[reflectionId]
    override suspend fun saveNote(reflectionId: String, noteText: String) {
        notes[reflectionId] = noteText
    }
}
