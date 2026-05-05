package com.mediasage.feature.figures

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mediasage.data.local.dao.EncouragementDao
import com.mediasage.domain.repository.FigureRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

class FiguresViewModel(
    private val figureRepository: FigureRepository,
    private val encouragementDao: EncouragementDao
) : ViewModel() {

    private val _state = MutableStateFlow<FiguresContract.UiState>(FiguresContract.UiState.Loading)
    val state: StateFlow<FiguresContract.UiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                figureRepository.getAllFigures(),
                encouragementDao.countByFigureName()
            ) { figures, counts ->
                figures.map { figure ->
                    VoiceFigureItem(
                        id = figure.id,
                        name = figure.name,
                        role = figure.role,
                        imageUrl = figure.portraitUrl,
                        quoteCount = counts[figure.name] ?: 0
                    )
                }
            }.collect { items ->
                _state.value = FiguresContract.UiState.Success(figures = items)
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
