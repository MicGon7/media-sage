package com.mediasage

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mediasage.domain.repository.FigureRepository
import kotlinx.coroutines.launch

class AppViewModel(
    private val figureRepository: FigureRepository
) : ViewModel() {

    init {
        viewModelScope.launch {
            try {
                figureRepository.syncFigures()
            } catch (e: Exception) {
                // Sync failure is non-fatal — app works offline with cached figures
            }
        }
    }
}
