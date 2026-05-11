package com.mediasage.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mediasage.domain.repository.AuthRepository
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _state = MutableStateFlow<SettingsContract.UiState>(SettingsContract.UiState.Ready)
    val state: StateFlow<SettingsContract.UiState> = _state.asStateFlow()

    private val _sideEffects = Channel<SettingsContract.SideEffect>(Channel.BUFFERED)
    val sideEffects = _sideEffects.receiveAsFlow()

    fun onIntent(intent: SettingsContract.Intent) {
        when (intent) {
            is SettingsContract.Intent.SignOut -> viewModelScope.launch {
                authRepository.signOut()
                _sideEffects.send(SettingsContract.SideEffect.SignedOut)
            }
        }
    }
}
