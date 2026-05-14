package com.mediasage.feature.you

object YouContract {

    sealed interface UiState {
        data class Ready(
            val displayName: String = "",
            val greeting: Greeting = Greeting.MORNING,
        ) : UiState
    }

    enum class Greeting { MORNING, AFTERNOON, EVENING }

    sealed interface Intent
}
