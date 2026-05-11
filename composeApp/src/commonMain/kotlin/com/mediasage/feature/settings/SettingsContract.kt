package com.mediasage.feature.settings

object SettingsContract {

    sealed interface UiState {
        data object Ready : UiState
    }

    sealed interface Intent {
        data object SignOut : Intent
    }

    sealed interface SideEffect {
        data object SignedOut : SideEffect
    }
}
