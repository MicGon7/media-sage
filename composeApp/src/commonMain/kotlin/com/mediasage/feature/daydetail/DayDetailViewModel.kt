package com.mediasage.feature.daydetail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mediasage.domain.model.DailyReflection
import com.mediasage.domain.model.DayDetailData
import com.mediasage.domain.usecase.GetDayDetailUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

/**
 * Read-only detail for a single day's briefings, pushed from
 * [com.mediasage.feature.you.ReaderHistoryScreen]. Which briefings are expanded is local-only state
 * combined with the live [GetDayDetailUseCase] stream — the reactive state-holder pattern, since the
 * briefing list can change while this screen is open. Morning starts expanded and evening collapsed
 * when both exist; a single briefing has no toggle and is always shown expanded (see
 * `DayDetailScreen`).
 */
class DayDetailViewModel(
    private val epochDay: Long,
    private val figureName: String?,
    private val figureImageUrl: String?,
    getDayDetail: GetDayDetailUseCase,
) : ViewModel() {

    private val expandedTones = MutableStateFlow(setOf(TONE_MORNING))

    val state: StateFlow<DayDetailContract.UiState> =
        combine(expandedTones, getDayDetail(epochDay)) { expanded, data -> buildReady(expanded, data) }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
                initialValue = DayDetailContract.UiState.Ready(
                    epochDay = epochDay,
                    figureName = figureName,
                    figureImageUrl = figureImageUrl,
                ),
            )

    fun onIntent(intent: DayDetailContract.Intent) {
        when (intent) {
            is DayDetailContract.Intent.BriefingToggled -> expandedTones.update { current ->
                if (intent.tone in current) current - intent.tone else current + intent.tone
            }
        }
    }

    private fun buildReady(
        expandedTones: Set<String>,
        data: DayDetailData,
    ): DayDetailContract.UiState.Ready = DayDetailContract.UiState.Ready(
        epochDay = epochDay,
        figureName = figureName,
        figureImageUrl = figureImageUrl,
        expandedTones = expandedTones,
        briefings = listOfNotNull(data.morningReflection, data.eveningReflection).map { it.toBriefingSummary() },
    )

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
        const val TONE_MORNING = "morning"
    }
}

private fun DailyReflection.toBriefingSummary() = DayDetailContract.BriefingSummary(
    scriptureReference = scriptureReference,
    scriptureText = scriptureText,
    insight = insight,
    implication = implication,
    inspiration = inspiration,
    sources = sources,
    tone = tone,
    theme = theme,
)
