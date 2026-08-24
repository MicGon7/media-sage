package com.mediasage.feature.daydetail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mediasage.domain.model.DailyReflection
import com.mediasage.domain.model.DayDetailData
import com.mediasage.domain.repository.UserReflectionNoteRepository
import com.mediasage.domain.usecase.GetDayDetailUseCase
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

/**
 * Read-only detail for a single day's briefings, pushed from
 * [com.mediasage.feature.you.ReaderHistoryScreen]. Which briefing tab is selected, and which one's
 * reflect sheet is open, are local-only state combined with the live [GetDayDetailUseCase] stream —
 * the reactive state-holder pattern, since the briefing list can change while this screen is open.
 * Morning | Evening tabs: exactly one tone is selected at a time, starting with morning; a single
 * briefing has no tab row and is always shown directly (see `DayDetailScreen`).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DayDetailViewModel(
    private val epochDay: Long,
    private val figureName: String?,
    private val figureImageUrl: String?,
    getDayDetail: GetDayDetailUseCase,
    private val userReflectionNoteRepository: UserReflectionNoteRepository,
) : ViewModel() {

    private val selectedTone = MutableStateFlow(TONE_MORNING)
    private val openReflectTone = MutableStateFlow<String?>(null)

    val state: StateFlow<DayDetailContract.UiState> =
        combine(selectedTone, openReflectTone, getDayDetail(epochDay)) { selected, openTone, data ->
            Inputs(selected, openTone, data)
        }
            .flatMapLatest { inputs -> flow { emit(buildReady(inputs)) } }
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
            is DayDetailContract.Intent.BriefingToneSelected -> selectedTone.update { intent.tone }
            is DayDetailContract.Intent.ReflectTapped -> openReflectTone.update { intent.tone }
            is DayDetailContract.Intent.ReflectDismissed -> openReflectTone.update { null }
        }
    }

    private suspend fun buildReady(inputs: Inputs): DayDetailContract.UiState.Ready {
        val briefings = listOfNotNull(inputs.data.morningReflection, inputs.data.eveningReflection)
            .map { it.toBriefingSummary() }
        return DayDetailContract.UiState.Ready(
            epochDay = epochDay,
            figureName = figureName,
            figureImageUrl = figureImageUrl,
            selectedTone = inputs.selectedTone,
            briefings = briefings,
            reflectSheet = inputs.openReflectTone?.let { tone -> buildReflectSheet(tone, briefings) },
        )
    }

    private suspend fun buildReflectSheet(
        tone: String,
        briefings: List<DayDetailContract.BriefingSummary>,
    ): DayDetailContract.ReflectSheetState? {
        val summary = briefings.firstOrNull { it.tone == tone } ?: return null
        val challenge = summary.challenge ?: return null
        val noteId = "${epochDay}_${tone}_${summary.theme ?: "NEWS"}"
        val note = userReflectionNoteRepository.getNote(noteId).orEmpty()
        return DayDetailContract.ReflectSheetState(tone = tone, challenge = challenge, noteText = note)
    }

    private data class Inputs(
        val selectedTone: String,
        val openReflectTone: String?,
        val data: DayDetailData,
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
    challenge = challenge,
)
