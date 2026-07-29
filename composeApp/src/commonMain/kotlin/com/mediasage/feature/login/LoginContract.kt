package com.mediasage.feature.login

object LoginContract {

    enum class Mode { SIGN_IN, SIGN_UP }

    data class UiState(
        val mode: Mode = Mode.SIGN_IN,
        val isLoading: Boolean = false,
        val error: String? = null,
        val rememberedEmail: String = "",
        val rememberEmail: Boolean = false,
        // Non-null once sign-up succeeds, while the OTP entry step is shown.
        val pendingOtpEmail: String? = null,
        val pendingDisplayName: String? = null,
    )

    sealed interface Intent {
        data class SignInWithEmail(val email: String, val password: String) : Intent
        data class SignUpWithEmail(val email: String, val password: String, val displayName: String) : Intent
        data class VerifyOtp(val code: String) : Intent
        data object SwitchToSignUp : Intent
        data object SwitchToSignIn : Intent
        data class ToggleRememberEmail(val enabled: Boolean) : Intent
        data object BypassAuth : Intent
    }

    sealed interface SideEffect {
        data object NavigateToHome : SideEffect
        data class ShowError(val message: String) : SideEffect
    }
}
