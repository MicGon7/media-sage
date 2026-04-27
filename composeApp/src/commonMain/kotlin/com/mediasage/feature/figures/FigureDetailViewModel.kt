package com.mediasage.feature.figures

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mediasage.data.local.dao.EncouragementDao
import com.mediasage.domain.repository.WikipediaRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

class FigureDetailViewModel(
    private val figureName: String,
    private val encouragementDao: EncouragementDao,
    private val wikipediaRepository: WikipediaRepository
) : ViewModel() {

    private val _state = MutableStateFlow<FigureDetailContract.UiState>(FigureDetailContract.UiState.Loading)
    val state: StateFlow<FigureDetailContract.UiState> = _state.asStateFlow()

    init {
        load()
    }

    private fun load() {
        viewModelScope.launch {
            try {
                val bio = wikipediaRepository.getBio(figureName)
                encouragementDao.getByFigureName(figureName).collect { entities ->
                    val first = entities.firstOrNull()
                    if (first == null) {
                        _state.value = FigureDetailContract.UiState.Error("Figure not found")
                        return@collect
                    }
                    _state.value = FigureDetailContract.UiState.Success(
                        figureName = first.figureName,
                        figureRole = first.figureRole,
                        figureImageUrl = first.figureImageUrl,
                        bio = bio,
                        quotes = entities.map { FigureQuoteItem(it.quoteText, it.headlineTitle) }
                    )
                }
            } catch (e: Exception) {
                _state.value = FigureDetailContract.UiState.Error(e.message.orEmpty())
            }
        }
    }
}
