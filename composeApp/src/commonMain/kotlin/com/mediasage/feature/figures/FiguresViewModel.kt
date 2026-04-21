package com.mediasage.feature.figures

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow

/** ViewModel for the Figures browser screen — implementation in a future ticket. */
class FiguresViewModel : ViewModel() {

    private val _state = MutableStateFlow<FiguresContract.UiState>(FiguresContract.UiState.Loading)
    val state: StateFlow<FiguresContract.UiState> = _state.asStateFlow()

    private val _sideEffects = Channel<FiguresContract.SideEffect>()
    val sideEffects = _sideEffects.receiveAsFlow()

    fun onIntent(intent: FiguresContract.Intent) {
        when (intent) {
            is FiguresContract.Intent.LoadFigures -> { /* TODO */ }
            is FiguresContract.Intent.FilterByCategory -> { /* TODO */ }
            is FiguresContract.Intent.FigureClicked -> { /* Handled via navigation callback */ }
        }
    }
}
