package com.mediasage.feature.figures

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mediasage.data.local.dao.EncouragementDao
import com.mediasage.domain.repository.FigureRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class FigureDetailViewModel(
    private val figureName: String,
    private val figureRepository: FigureRepository,
    private val encouragementDao: EncouragementDao
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
                encouragementDao.getByFigureName(figureName).collect { entities ->
                    _state.value = FigureDetailContract.UiState.Success(
                        figureName = figure?.name ?: figureName,
                        figureRole = figure?.role ?: entities.firstOrNull()?.figureRole.orEmpty(),
                        figureImageUrl = entities.firstOrNull()?.figureImageUrl,
                        bio = figure?.bio,
                        quotes = entities.map { FigureQuoteItem(it.quoteText, it.headlineTitle) }
                    )
                }
            } catch (e: Exception) {
                _state.value = FigureDetailContract.UiState.Error(e.message.orEmpty())
            }
        }
    }
}
