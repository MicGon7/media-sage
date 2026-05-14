package com.mediasage.feature.you

import androidx.lifecycle.ViewModel
import com.mediasage.data.repository.epochMillis
import com.mediasage.domain.repository.AuthRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

class YouViewModel(
    authRepository: AuthRepository,
) : ViewModel() {

    private val _state = MutableStateFlow<YouContract.UiState>(
        YouContract.UiState.Ready(
            displayName = authRepository.currentSession()?.let { session ->
                session.displayName?.substringBefore(" ") ?: session.email ?: ""
            } ?: "",
            greeting = currentGreeting(),
        )
    )

    private fun currentGreeting(): YouContract.Greeting {
        val hour = Instant.fromEpochMilliseconds(epochMillis())
            .toLocalDateTime(TimeZone.currentSystemDefault()).hour
        return when {
            hour < 12 -> YouContract.Greeting.MORNING
            hour < 17 -> YouContract.Greeting.AFTERNOON
            else -> YouContract.Greeting.EVENING
        }
    }
    val state: StateFlow<YouContract.UiState> = _state.asStateFlow()

    fun onIntent(intent: YouContract.Intent) {}
}
