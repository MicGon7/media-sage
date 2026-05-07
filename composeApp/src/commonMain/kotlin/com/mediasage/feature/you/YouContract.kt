package com.mediasage.feature.you

object YouContract {

    sealed interface UiState {
        data class Ready(val darkMode: Boolean = false) : UiState
    }

    sealed interface Intent {
        data class ToggleDarkMode(val enabled: Boolean) : Intent
    }
}
