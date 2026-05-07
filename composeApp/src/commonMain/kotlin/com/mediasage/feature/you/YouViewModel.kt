package com.mediasage.feature.you

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mediasage.data.ThemePreferencesRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class YouViewModel(
    private val themePreferencesRepository: ThemePreferencesRepository
) : ViewModel() {

    private val _state = MutableStateFlow<YouContract.UiState>(YouContract.UiState.Ready())
    val state: StateFlow<YouContract.UiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            themePreferencesRepository.darkMode.collect { dark ->
                _state.value = YouContract.UiState.Ready(darkMode = dark)
            }
        }
    }

    fun onIntent(intent: YouContract.Intent) {
        when (intent) {
            is YouContract.Intent.ToggleDarkMode -> {
                viewModelScope.launch { themePreferencesRepository.setDarkMode(intent.enabled) }
            }
        }
    }
}
