@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.mediasage.feature.daydetail

import com.mediasage.domain.model.BriefingDay
import com.mediasage.domain.model.DailyReflection
import com.mediasage.domain.repository.DailyReflectionRepository
import com.mediasage.domain.usecase.GetDayDetailUseCase
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
import kotlin.test.assertTrue

class DayDetailViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    private fun briefing(tone: String) = DailyReflection(
        scriptureReference = "John 3:16",
        scriptureText = "For God so loved the world",
        insight = "insight-$tone",
        implication = "implication-$tone",
        inspiration = "inspiration-$tone",
        sources = emptyList(),
        tone = tone,
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
    fun bothBriefingsPresent_showsTwoBriefingSummaries() = runTest(testDispatcher) {
        val viewModel = dayDetailViewModel(
            morning = briefing("morning"),
            evening = briefing("evening"),
        )

        val state = viewModel.state.value as DayDetailContract.UiState.Ready
        assertEquals(2, state.briefings.size)
    }

    @Test
    fun onlyOneBriefingGenerated_showsSingleBriefingGracefully() = runTest(testDispatcher) {
        val viewModel = dayDetailViewModel(morning = briefing("morning"), evening = null)

        val state = viewModel.state.value as DayDetailContract.UiState.Ready
        assertEquals(1, state.briefings.size)
        assertEquals("morning", state.briefings.first().tone)
    }

    @Test
    fun noBriefingsGenerated_briefingsListIsEmpty() = runTest(testDispatcher) {
        val viewModel = dayDetailViewModel(morning = null, evening = null)

        val state = viewModel.state.value as DayDetailContract.UiState.Ready
        assertTrue(state.briefings.isEmpty())
    }

    @Test
    fun briefingSummaryCarriesSourcesAndTheme() = runTest(testDispatcher) {
        val morning = DailyReflection(
            scriptureReference = "John 3:16",
            scriptureText = "For God so loved the world",
            insight = "insight-morning",
            implication = "implication-morning",
            inspiration = "inspiration-morning",
            sources = listOf("Mere Christianity, Book IV"),
            tone = "morning",
            theme = "HOPE",
        )
        val viewModel = dayDetailViewModel(morning = morning, evening = null)

        val state = viewModel.state.value as DayDetailContract.UiState.Ready
        val summary = state.briefings.first()
        assertEquals(listOf("Mere Christianity, Book IV"), summary.sources)
        assertEquals("HOPE", summary.theme)
    }

    @Test
    fun morningStartsExpandedEveningStartsCollapsed() = runTest(testDispatcher) {
        val viewModel = dayDetailViewModel(morning = briefing("morning"), evening = briefing("evening"))

        val state = viewModel.state.value as DayDetailContract.UiState.Ready
        assertEquals("morning", state.expandedTone)
    }

    @Test
    fun expandingEveningCollapsesMorning() = runTest(testDispatcher) {
        val viewModel = dayDetailViewModel(morning = briefing("morning"), evening = briefing("evening"))

        viewModel.onIntent(DayDetailContract.Intent.BriefingToggled("evening"))

        val state = viewModel.state.value as DayDetailContract.UiState.Ready
        assertEquals("evening", state.expandedTone)
    }

    @Test
    fun toggledTwiceReturnsToDefault() = runTest(testDispatcher) {
        val viewModel = dayDetailViewModel(morning = briefing("morning"), evening = briefing("evening"))

        viewModel.onIntent(DayDetailContract.Intent.BriefingToggled("morning"))
        viewModel.onIntent(DayDetailContract.Intent.BriefingToggled("morning"))

        val state = viewModel.state.value as DayDetailContract.UiState.Ready
        assertEquals("morning", state.expandedTone)
    }

    @Test
    fun toggledOnceCollapsesMorning() = runTest(testDispatcher) {
        val viewModel = dayDetailViewModel(morning = briefing("morning"), evening = briefing("evening"))

        viewModel.onIntent(DayDetailContract.Intent.BriefingToggled("morning"))

        val state = viewModel.state.value as DayDetailContract.UiState.Ready
        assertEquals(null, state.expandedTone)
    }

    /**
     * Builds the ViewModel and starts collecting its state. `stateIn(WhileSubscribed)` is cold
     * until a subscriber is present, so an active collector in [backgroundScope] is required for
     * `state.value` to reflect the pipeline output.
     */
    private fun TestScope.dayDetailViewModel(
        morning: DailyReflection? = null,
        evening: DailyReflection? = null,
    ): DayDetailViewModel {
        val reflectionRepo = FakeDailyReflectionRepository(morning, evening)
        val viewModel = DayDetailViewModel(
            epochDay = 10L,
            figureName = "Augustine of Hippo",
            figureImageUrl = null,
            getDayDetail = GetDayDetailUseCase(reflectionRepo),
        )
        backgroundScope.launch(testDispatcher) { viewModel.state.collect {} }
        return viewModel
    }
}

private class FakeDailyReflectionRepository(
    private val morning: DailyReflection?,
    private val evening: DailyReflection?,
) : DailyReflectionRepository {
    override suspend fun getOrFetch(
        figureId: Long,
        figureName: String,
        headlines: List<String>,
        tone: String,
        theme: String?,
    ): DailyReflection = throw UnsupportedOperationException()

    override fun observeByEpochDayRange(startEpochDay: Long, endEpochDay: Long): Flow<List<BriefingDay>> =
        MutableStateFlow(emptyList())

    override suspend fun getForDay(epochDay: Long, tone: String): DailyReflection? =
        if (tone == "morning") morning else evening

    override suspend fun getEarliestBriefingEpochDay(): Long? = null

    override suspend fun getLockedFigureId(epochDay: Long): Long? = null

    override val isResolved: StateFlow<Boolean> = MutableStateFlow(true)
    override suspend fun resolve(userId: String?) = Unit
}
