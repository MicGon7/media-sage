package com.mediasage.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mediasage.data.ThemePreferencesRepository
import com.mediasage.domain.repository.AuthRepository
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val authRepository: AuthRepository,
    private val themePreferencesRepository: ThemePreferencesRepository,
) : ViewModel() {

    private val _state = MutableStateFlow<SettingsContract.UiState>(SettingsContract.UiState.Ready())
    val state: StateFlow<SettingsContract.UiState> = _state.asStateFlow()

    private val _sideEffects = Channel<SettingsContract.SideEffect>(Channel.BUFFERED)
    val sideEffects = _sideEffects.receiveAsFlow()

    init {
        viewModelScope.launch {
            combine(
                themePreferencesRepository.appTheme,
                themePreferencesRepository.darkMode,
            ) { theme, dark ->
                SettingsContract.UiState.Ready(appTheme = theme, darkMode = dark)
            }.collect { _state.value = it }
        }
    }

    fun onIntent(intent: SettingsContract.Intent) {
        when (intent) {
            is SettingsContract.Intent.SetAppTheme -> viewModelScope.launch {
                themePreferencesRepository.setAppTheme(intent.theme)
            }
            is SettingsContract.Intent.ToggleDarkMode -> viewModelScope.launch {
                themePreferencesRepository.setDarkMode(intent.enabled)
            }
            is SettingsContract.Intent.SignOut -> viewModelScope.launch {
                authRepository.signOut()
                _sideEffects.send(SettingsContract.SideEffect.SignedOut)
            }
        }
    }
}
