package com.mediasage.feature.headlinedetail

import com.mediasage.ui.ErrorType

/** MVI contract for the Headline Detail feature. */
object HeadlineDetailContract {

    sealed interface UiState {
        data object Loading : UiState
        data class Success(
            // Headline data — available immediately from Room
            val headlineTitle: String,
            val headlineSource: String,
            val headlineCategory: String,
            val headlineImageUrl: String?,
            // AI content — loaded progressively from Claude
            val encouragement: EncouragementState = EncouragementState.Loading,
            val isBookmarked: Boolean = false,
            val figureProfile: FigureProfileState? = null
        ) : UiState
        data class Error(val errorType: ErrorType) : UiState
    }

    data class FigureProfileState(
        val name: String,
        val role: String,
        val imageUrl: String?,
        val bio: String?
    )

    sealed interface EncouragementState {
        data object Loading : EncouragementState
        data class Loaded(
            val summary: String?,
            val quoteText: String,
            val figureName: String,
            val figureRole: String,
            val figureImageUrl: String?,
            val scriptureReference: String,
            val scriptureText: String,
            val matchExplanation: String,
            val matchTheme: String = "",
            val tone: String = "",
            val figureId: Long? = null,
        ) : EncouragementState
        data class Error(val errorType: ErrorType) : EncouragementState
    }

    sealed interface Intent {
        data object RetryMatch : Intent
        data object ToggleBookmark : Intent
        data object ViewFigureProfile : Intent
        data object DismissFigureProfile : Intent
    }

    sealed interface SideEffect {
        data class ShowError(val message: String) : SideEffect
    }
}
