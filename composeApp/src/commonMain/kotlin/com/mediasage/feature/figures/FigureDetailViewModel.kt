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
    private val figureName: String,
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
            try {
                val figure = figureRepository.getFigureByName(figureName)
                encouragementRepository.getByFigureName(figureName).collect { encouragements ->
                    _state.value = FigureDetailContract.UiState.Success(
                        figureName = figure?.name ?: figureName,
                        figureRole = figure?.role ?: encouragements.firstOrNull()?.figureRole.orEmpty(),
                        figureImageUrl = figure?.portraitUrl ?: encouragements.firstOrNull()?.figureImageUrl,
                        bio = figure?.bio,
                        quotes = encouragements.map { FigureQuoteItem(it.quoteText, it.headlineTitle) }
                    )
                }
            } catch (e: Exception) {
                _state.value = FigureDetailContract.UiState.Error(e.message.orEmpty())
            }
        }
    }
}
