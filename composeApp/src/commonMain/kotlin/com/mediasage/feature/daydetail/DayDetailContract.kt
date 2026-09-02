package com.mediasage.feature.daydetail

object DayDetailContract {

    data class BriefingSummary(
        val scriptureReference: String,
        val scriptureText: String,
        val insight: String,
        val implication: String,
        val inspiration: String,
        val sources: List<String>,
        val tone: String,
        val theme: String? = null,
        val challenge: String? = null,
    )

    /**
     * Always read-only — reached only from past briefings, never the active tone slot. [noteText]
     * is null while the saved note is still loading — the sheet opens immediately on tap with
     * [challenge] visible (see `DayDetailViewModel.openReflectSheet`).
     */
    data class ReflectSheetState(
        val tone: String,
        val challenge: String,
        val noteText: String?,
    )

    sealed interface UiState {
        data class Ready(
            val epochDay: Long,
            val figureName: String? = null,
            val figureImageUrl: String? = null,
            val selectedTone: String? = null,
            val briefings: List<BriefingSummary> = emptyList(),
            val reflectSheet: ReflectSheetState? = null,
        ) : UiState
    }

    sealed interface Intent {
        data class BriefingToneSelected(val tone: String) : Intent
        data class ReflectTapped(val tone: String) : Intent
        data object ReflectDismissed : Intent
    }
}
