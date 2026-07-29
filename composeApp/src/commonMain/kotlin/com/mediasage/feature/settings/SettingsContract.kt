package com.mediasage.feature.settings

import com.mediasage.theme.AppTheme

object SettingsContract {

    sealed interface UiState {
        data class Ready(
            val appTheme: AppTheme = AppTheme.CLASSIC,
            val darkMode: Boolean = false,
            val appVersion: String = "1.0",
            val displayName: String = "",
        ) : UiState
    }

    sealed interface Intent {
        data class SetAppTheme(val theme: AppTheme) : Intent
        data class ToggleDarkMode(val enabled: Boolean) : Intent
        data object SignOut : Intent
    }

    sealed interface SideEffect {
        data object SignedOut : SideEffect
    }
}
