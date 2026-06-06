package com.mediasage.feature.briefing

import com.mediasage.ui.ErrorType

object BriefingContract {

    sealed interface UiState {
        data class Loading(val todayLabel: String) : UiState
        data class Success(
            val todayLabel: String,
            val card: CardState
        ) : UiState
        data class Error(val errorType: ErrorType) : UiState
    }

    sealed interface CardState {
        data object Hidden : CardState
        data object Loading : CardState
        data class LoadingWithFigure(
            val figureId: Long,
            val figureName: String,
            val figureImageUrl: String?
        ) : CardState
        data class Ready(
            val figureId: Long,
            val figureName: String,
            val figureImageUrl: String?,
            val scriptureReference: String,
            val scriptureText: String,
            val reflection: String,
            val sources: List<String>,
            val tone: String
        ) : CardState
    }

    sealed interface Intent {
        data object Retry : Intent
    }

    sealed interface SideEffect {
        data class ShowError(val message: String) : SideEffect
    }
}
