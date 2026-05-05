package com.mediasage.feature.figures

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mediasage.domain.repository.EncouragementRepository
import com.mediasage.domain.repository.FigureRepository
import com.mediasage.domain.repository.PinnedFigureRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class FigureDetailViewModel(
    private val figureId: Long,
    private val figureRepository: FigureRepository,
    private val encouragementRepository: EncouragementRepository,
    private val pinnedFigureRepository: PinnedFigureRepository
) : ViewModel() {

    private val _state = MutableStateFlow<FigureDetailContract.UiState>(FigureDetailContract.UiState.Loading)
    val state: StateFlow<FigureDetailContract.UiState> = _state.asStateFlow()

    init {
        load()
        observePinState()
    }

    fun onIntent(intent: FigureDetailContract.Intent) {
        when (intent) {
            is FigureDetailContract.Intent.PinToHome -> {
                val current = _state.value as? FigureDetailContract.UiState.Success ?: return
                viewModelScope.launch {
                    pinnedFigureRepository.setPinnedFigureId(if (current.isPinned) null else figureId)
                }
            }
        }
    }

    private fun load() {
        viewModelScope.launch {
            val figure = figureRepository.getFigureById(figureId) ?: return@launch
            encouragementRepository.getByFigureId(figure.id).collect { encouragements ->
                val current = _state.value
                val isPinned = if (current is FigureDetailContract.UiState.Success) current.isPinned else false
                _state.value = FigureDetailContract.UiState.Success(
                    figureName = figure.name,
                    figureRole = figure.role,
                    figureImageUrl = figure.portraitUrl,
                    bio = figure.bio,
                    quotes = encouragements.map { FigureQuoteItem(it.quoteText, it.headlineTitle) },
                    isPinned = isPinned
                )
            }
        }
    }

    private fun observePinState() {
        viewModelScope.launch {
            pinnedFigureRepository.observePinnedFigureId().collect { pinnedId ->
                val current = _state.value
                if (current is FigureDetailContract.UiState.Success) {
                    _state.value = current.copy(isPinned = pinnedId == figureId)
                }
            }
        }
    }
}
