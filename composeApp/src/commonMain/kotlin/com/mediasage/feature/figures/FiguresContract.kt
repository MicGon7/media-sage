package com.mediasage.feature.figures

import com.mediasage.domain.model.FigureCategory

/** MVI contract for the Figures browser feature. */
object FiguresContract {

    sealed interface UiState {
        data object Loading : UiState
        data class Success(
            val figures: List<FigureItem>,
            val selectedCategory: FigureCategory? = null
        ) : UiState
        data class Error(val message: String) : UiState
    }

    sealed interface Intent {
        data object LoadFigures : Intent
        data class FilterByCategory(val category: FigureCategory?) : Intent
        data class FigureClicked(val figureId: Long) : Intent
    }

    sealed interface SideEffect {
        data class NavigateToFigureDetail(val figureId: Long) : SideEffect
        data class ShowError(val message: String) : SideEffect
    }
}

data class FigureItem(
    val id: Long,
    val name: String,
    val category: FigureCategory,
    val century: String
)
