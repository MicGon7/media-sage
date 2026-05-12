package com.mediasage.feature.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mediasage.data.UserPreferencesRepository
import com.mediasage.domain.repository.AuthRepository
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class LoginViewModel(
    private val authRepository: AuthRepository,
    private val userPreferencesRepository: UserPreferencesRepository
) : ViewModel() {

    private val _state = MutableStateFlow(LoginContract.UiState())
    val state: StateFlow<LoginContract.UiState> = _state.asStateFlow()

    private val _sideEffects = Channel<LoginContract.SideEffect>(Channel.BUFFERED)
    val sideEffects = _sideEffects.receiveAsFlow()

    init {
        viewModelScope.launch {
            val email = userPreferencesRepository.rememberedEmail.first()
            if (email.isNotBlank()) {
                _state.update { it.copy(rememberedEmail = email, rememberEmail = true) }
            }
        }
    }

    fun onIntent(intent: LoginContract.Intent) {
        when (intent) {
            is LoginContract.Intent.SignInWithEmail -> signIn(intent.email, intent.password)
            is LoginContract.Intent.ToggleRememberEmail -> _state.update {
                it.copy(rememberEmail = intent.enabled)
            }
            is LoginContract.Intent.BypassAuth -> viewModelScope.launch {
                _sideEffects.send(LoginContract.SideEffect.NavigateToHome)
            }
        }
    }

    private fun signIn(email: String, password: String) {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            runCatching { authRepository.signInWithEmail(email, password) }
                .onSuccess {
                    val rememberEmail = _state.value.rememberEmail
                    if (rememberEmail) {
                        userPreferencesRepository.setRememberedEmail(email)
                    } else {
                        userPreferencesRepository.clearRememberedEmail()
                    }
                    _state.update { it.copy(isLoading = false, error = null) }
                    _sideEffects.send(LoginContract.SideEffect.NavigateToHome)
                }
                .onFailure { e ->
                    _state.update { it.copy(isLoading = false, error = e.message ?: "Sign in failed") }
                    _sideEffects.send(LoginContract.SideEffect.ShowError(e.message ?: "Sign in failed"))
                }
        }
    }
}
