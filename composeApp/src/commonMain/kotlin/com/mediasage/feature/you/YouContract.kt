package com.mediasage.feature.you

object YouContract {

    sealed interface UiState {
        data object Ready : UiState
    }

    sealed interface Intent
}
