package com.mediasage.feature.figures

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mediasage.data.local.dao.EncouragementDao
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class FiguresViewModel(
    private val encouragementDao: EncouragementDao
) : ViewModel() {

    private val _state = MutableStateFlow<FiguresContract.UiState>(FiguresContract.UiState.Loading)
    val state: StateFlow<FiguresContract.UiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            encouragementDao.getDistinctFigures().collect { projections ->
                _state.value = FiguresContract.UiState.Success(
                    figures = projections.map { VoiceFigureItem(it.figureName, it.figureRole, it.figureImageUrl) }
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
