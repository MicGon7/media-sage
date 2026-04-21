package com.mediasage.feature.match

/** MVI contract for the Match feature. */
object MatchContract {

    sealed interface UiState {
        data object Loading : UiState
        data class Success(
            val headlineTitle: String,
            val headlineSource: String,
            val headlineCategory: String,
            val quoteText: String,
            val figureName: String,
            val figureRole: String,
            val scriptureReference: String,
            val matchExplanation: String,
            val matchTheme: String = "",
        ) : UiState
        data class Error(val message: String) : UiState
    }

    sealed interface Intent {
        data class LoadMatch(val headlineId: Long) : Intent
        data object RetryMatch : Intent
    }

    sealed interface SideEffect {
        data class ShowError(val message: String) : SideEffect
    }
}
