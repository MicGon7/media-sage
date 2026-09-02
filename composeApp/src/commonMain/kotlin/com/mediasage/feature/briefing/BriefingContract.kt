package com.mediasage.feature.briefing

import com.mediasage.ui.ErrorType

object BriefingContract {

    sealed interface UiState {
        data class Loading(val todayLabel: String) : UiState
        data class Success(
            val todayLabel: String,
            val card: CardState,
            val reflectSheet: ReflectSheetState? = null,
        ) : UiState
        data class Error(val errorType: ErrorType) : UiState
    }

    sealed interface CardState {
        data object Hidden : CardState
        data object Loading : CardState
        data class LoadingWithFigure(
            val figureId: Long,
            val figureName: String,
            val figureImageUrl: String?,
            val theme: String? = null
        ) : CardState
        data class Ready(
            val figureId: Long,
            val figureName: String,
            val figureImageUrl: String?,
            val scriptureReference: String,
            val scriptureText: String,
            val insight: String,
            val implication: String,
            val inspiration: String,
            val sources: List<String>,
            val tone: String,
            val theme: String? = null,
            val challenge: String? = null,
        ) : CardState
    }

    /**
     * [editable] is always true here — the live Briefing screen only ever shows the active tone.
     * [noteText]/[savedNoteText] are null while the existing note is still loading — the sheet
     * opens immediately on tap with [challenge] visible, and the note field shows a loading state
     * until these resolve (see `BriefingViewModel.openReflectSheet`).
     */
    data class ReflectSheetState(
        val challenge: String,
        val noteText: String?,
        val savedNoteText: String?,
        val editable: Boolean = true,
    )

    sealed interface Intent {
        data object Retry : Intent
        data object ReflectTapped : Intent
        data object ReflectDismissed : Intent
        data class ReflectNoteChanged(val noteText: String) : Intent
        data object ReflectNoteSaved : Intent
    }

    sealed interface SideEffect {
        data class ShowError(val message: String) : SideEffect
    }
}
