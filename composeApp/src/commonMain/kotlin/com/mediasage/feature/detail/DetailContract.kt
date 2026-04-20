package com.mediasage.feature.detail

/** MVI contract for the Match Detail feature. */
object DetailContract {

    sealed interface UiState {
        data object Loading : UiState
        data class Success(
            val headlineTitle: String,
            val quoteText: String,
            val figureName: String,
            val matchExplanation: String,
            val confidence: Float,
            val connectionThemes: List<String>
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
