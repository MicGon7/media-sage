package com.mediasage.feature.login

object LoginContract {

    data class UiState(
        val isLoading: Boolean = false,
        val error: String? = null,
        val rememberedEmail: String = "",
        val rememberEmail: Boolean = false
    )

    sealed interface Intent {
        data class SignInWithEmail(val email: String, val password: String) : Intent
        data class ToggleRememberEmail(val enabled: Boolean) : Intent
        data object BypassAuth : Intent
    }

    sealed interface SideEffect {
        data object NavigateToHome : SideEffect
        data class ShowError(val message: String) : SideEffect
    }
}
