package com.mediasage

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mediasage.data.ThemePreferencesRepository
import com.mediasage.domain.repository.FigureRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class AppViewModel(
    private val figureRepository: FigureRepository,
    themePreferencesRepository: ThemePreferencesRepository,
) : ViewModel() {

    val darkMode: StateFlow<Boolean> = themePreferencesRepository.darkMode
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

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
