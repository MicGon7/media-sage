package com.mediasage.feature.figures

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mediasage.domain.repository.FigureRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class FiguresViewModel(
    private val figureRepository: FigureRepository
) : ViewModel() {

    private val _state = MutableStateFlow<FiguresContract.UiState>(FiguresContract.UiState.Loading)
    val state: StateFlow<FiguresContract.UiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            figureRepository.getAllFigures().collect { figures ->
                _state.value = FiguresContract.UiState.Success(
                    figures = figures.map { VoiceFigureItem(it.name, it.role, it.portraitUrl) }
                )
            }
        }
    }

    fun onIntent(intent: FiguresContract.Intent) {
        when (intent) {
            is FiguresContract.Intent.LoadFigures -> { /* reactive — no manual reload needed */ }
            is FiguresContract.Intent.FigureClicked -> { /* handled via navigation callback */ }
        }
    }
}
