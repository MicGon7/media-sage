@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.mediasage.feature.daydetail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mediasage.domain.model.DailyReflection
import com.mediasage.domain.model.DayDetailData
import com.mediasage.domain.repository.UserReflectionNoteRepository
import com.mediasage.domain.usecase.GetDayDetailUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.flow.update

/**
 * Read-only detail for a single day's briefings, pushed from
 * [com.mediasage.feature.you.ReaderHistoryScreen]. Which briefing tab is selected, and which one's
 * reflect sheet is open, are local-only state combined with the live [GetDayDetailUseCase] stream —
 * the reactive state-holder pattern, since the briefing list can change while this screen is open.
 * Morning | Evening tabs: exactly one tone is selected at a time, starting with morning; a single
 * briefing has no tab row and is always shown directly (see `DayDetailScreen`).
 */
class DayDetailViewModel(
    private val epochDay: Long,
    private val figureName: String?,
    private val figureImageUrl: String?,
    getDayDetail: GetDayDetailUseCase,
    private val userReflectionNoteRepository: UserReflectionNoteRepository,
) : ViewModel() {

    private val selectedTone = MutableStateFlow(TONE_MORNING)
    private val openReflectTone = MutableStateFlow<String?>(null)

    // Shared (`stateIn`) rather than a plain cold Flow so the two `combine` calls below that read
    // it — one for `loadedNoteText`, one for `state` — collect the underlying use case's Room
    // query exactly once, not once per combine.
    private val dayDetail: StateFlow<DayDetailData> = getDayDetail(epochDay)
        .stateIn(scope = viewModelScope, started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), initialValue = DayDetailData(null, null))

    /**
     * Derived, not pushed from the intent handler — `combine`+`transformLatest` re-fetches
     * whenever [openReflectTone] or [dayDetail] changes, but a `StateFlow` always has a value
     * ready (`null` until the fetch resolves) without the downstream [state] combine ever waiting
     * on the suspend call inside [loadNoteTextOrNull]. This is what lets the sheet open
     * immediately on [openReflectTone] alone — a first-time shared-key fetch (MS-740) inside
     * `getNote` only delays this field, never the sheet itself. `transformLatest` emits `null`
     * up front on every new (tone, data) tick — plain `mapLatest` would instead leave the
     * *previous* tone's resolved text in place until the new fetch completes, showing the wrong
     * tone's note text for however long that fetch takes.
     */
    private val loadedNoteText: StateFlow<String?> =
        combine(openReflectTone, dayDetail) { tone, data -> tone to data }
            .transformLatest { (tone, data) ->
                emit(null)
                emit(loadNoteTextOrNull(tone, data))
            }
            .stateIn(scope = viewModelScope, started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), initialValue = null)

    val state: StateFlow<DayDetailContract.UiState> =
        combine(selectedTone, openReflectTone, loadedNoteText, dayDetail) { selected, openTone, noteText, data ->
            buildReady(Inputs(selected, openTone, noteText, data))
        }
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

    private suspend fun loadNoteTextOrNull(tone: String?, data: DayDetailData): String? {
        if (tone == null) return null
        val summary = listOfNotNull(data.morningReflection, data.eveningReflection)
            .map { it.toBriefingSummary() }
            .firstOrNull { it.tone == tone } ?: return null
        val noteId = DailyReflection.id(epochDay, tone, summary.theme)
        return userReflectionNoteRepository.getNote(noteId).orEmpty()
    }

    private fun buildReady(inputs: Inputs): DayDetailContract.UiState.Ready {
        val briefings = listOfNotNull(inputs.data.morningReflection, inputs.data.eveningReflection)
            .map { it.toBriefingSummary() }
        return DayDetailContract.UiState.Ready(
            epochDay = epochDay,
            figureName = figureName,
            figureImageUrl = figureImageUrl,
            selectedTone = inputs.selectedTone,
            briefings = briefings,
            reflectSheet = inputs.openReflectTone?.let { tone -> buildReflectSheet(tone, inputs.noteText, briefings) },
        )
    }

    private fun buildReflectSheet(
        tone: String,
        noteText: String?,
        briefings: List<DayDetailContract.BriefingSummary>,
    ): DayDetailContract.ReflectSheetState? {
        val summary = briefings.firstOrNull { it.tone == tone } ?: return null
        val challenge = summary.challenge ?: return null
        return DayDetailContract.ReflectSheetState(tone = tone, challenge = challenge, noteText = noteText)
    }

    private data class Inputs(
        val selectedTone: String,
        val openReflectTone: String?,
        val noteText: String?,
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
