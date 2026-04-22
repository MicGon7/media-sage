package com.mediasage.feature.match

import com.mediasage.ui.ErrorType

/** MVI contract for the Match (Headline Detail) feature. */
object MatchContract {

    sealed interface UiState {
        data object Loading : UiState
        data class Success(
            val headlineTitle: String,
            val headlineSource: String,
            val headlineCategory: String,
            val headlineImageUrl: String?,
            val summary: String?,
            val quoteText: String,
            val figureName: String,
            val figureRole: String,
            val scriptureReference: String,
            val scriptureText: String,
            val matchExplanation: String,
            val matchTheme: String = "",
            val tone: String = "",
        ) : UiState
        data class Error(val errorType: ErrorType) : UiState
    }

    sealed interface Intent {
        data object RetryMatch : Intent
    }

    sealed interface SideEffect {
        data class ShowError(val message: String) : SideEffect
    }
}
