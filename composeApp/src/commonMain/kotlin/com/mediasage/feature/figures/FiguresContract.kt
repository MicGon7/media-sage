package com.mediasage.feature.figures

object FiguresContract {

    sealed interface UiState {
        data object Loading : UiState
        data class Success(val figures: List<VoiceFigureItem>) : UiState
    }

    sealed interface Intent {
        data object LoadFigures : Intent
        data class FigureClicked(val figureName: String) : Intent
    }
}

data class VoiceFigureItem(
    val name: String,
    val role: String,
    val imageUrl: String?
)
