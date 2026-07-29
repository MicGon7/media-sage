package com.mediasage.feature.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mediasage.data.AuthPreferencesRepository
import com.mediasage.domain.repository.AuthRepository
import com.mediasage.domain.repository.ProfileRepository
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
    private val userPreferencesRepository: AuthPreferencesRepository,
    private val profileRepository: ProfileRepository,
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
            is LoginContract.Intent.SignUpWithEmail -> signUp(intent.email, intent.password, intent.displayName)
            is LoginContract.Intent.VerifyOtp -> verifyOtp(intent.code)
            is LoginContract.Intent.SwitchToSignUp -> _state.update {
                it.copy(mode = LoginContract.Mode.SIGN_UP, error = null, pendingOtpEmail = null, pendingDisplayName = null)
            }
            is LoginContract.Intent.SwitchToSignIn -> _state.update {
                it.copy(mode = LoginContract.Mode.SIGN_IN, error = null, pendingOtpEmail = null, pendingDisplayName = null)
            }
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

    private fun signUp(email: String, password: String, displayName: String) {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            runCatching { authRepository.signUp(email, password, displayName) }
                .onSuccess {
                    _state.update {
                        it.copy(isLoading = false, pendingOtpEmail = email, pendingDisplayName = displayName)
                    }
                }
                .onFailure { e ->
                    _state.update { it.copy(isLoading = false, error = e.message ?: "Sign up failed") }
                    _sideEffects.send(LoginContract.SideEffect.ShowError(e.message ?: "Sign up failed"))
                }
        }
    }

    private fun verifyOtp(code: String) {
        val email = _state.value.pendingOtpEmail ?: return
        val displayName = _state.value.pendingDisplayName.orEmpty()
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            runCatching { authRepository.verifySignUpOtp(email, code) }
                .onSuccess {
                    createProfileBestEffort(displayName)
                    // The ViewModel outlives sign-out (it is scoped to the Activity, not the login
                    // screen), so leaving the OTP step in state would resurface it on the next visit.
                    _state.update {
                        it.copy(
                            mode = LoginContract.Mode.SIGN_IN,
                            isLoading = false,
                            error = null,
                            pendingOtpEmail = null,
                            pendingDisplayName = null,
                        )
                    }
                    _sideEffects.send(LoginContract.SideEffect.NavigateToHome)
                }
                .onFailure { e ->
                    _state.update { it.copy(isLoading = false, error = e.message ?: "Invalid code") }
                    _sideEffects.send(LoginContract.SideEffect.ShowError(e.message ?: "Invalid code"))
                }
        }
    }

    // Best-effort: the account is already verified at this point, so a profile-row failure
    // (e.g. the profiles table migration hasn't been run yet) must not block sign-up completion.
    private suspend fun createProfileBestEffort(displayName: String) {
        val userId = authRepository.currentSession()?.userId?.takeIf { it.isNotBlank() } ?: return
        runCatching { profileRepository.createProfile(userId, displayName) }
    }
}
