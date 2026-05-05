package com.mediasage.feature.figures

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mediasage.domain.repository.EncouragementRepository
import com.mediasage.domain.repository.FigureRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class FigureDetailViewModel(
    private val figureId: Long,
    private val figureRepository: FigureRepository,
    private val encouragementRepository: EncouragementRepository
) : ViewModel() {

    private val _state = MutableStateFlow<FigureDetailContract.UiState>(FigureDetailContract.UiState.Loading)
    val state: StateFlow<FigureDetailContract.UiState> = _state.asStateFlow()

    init {
        load()
    }

    private fun load() {
        viewModelScope.launch {
            val figure = figureRepository.getFigureById(figureId) ?: return@launch
            encouragementRepository.getByFigureId(figure.id).collect { encouragements ->
                _state.value = FigureDetailContract.UiState.Success(
                    figureName = figure.name,
                    figureRole = figure.role,
                    figureImageUrl = figure.portraitUrl,
                    bio = figure.bio,
                    quotes = encouragements.map { FigureQuoteItem(it.quoteText, it.headlineTitle) }
                )
            }
        }
    }
}
