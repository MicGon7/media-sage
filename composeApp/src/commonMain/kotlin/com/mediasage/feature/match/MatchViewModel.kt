package com.mediasage.feature.match

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow

/** ViewModel for the Match screen — implementation in MS-14. */
class MatchViewModel : ViewModel() {

    private val _state = MutableStateFlow<MatchContract.UiState>(MatchContract.UiState.Loading)
    val state: StateFlow<MatchContract.UiState> = _state.asStateFlow()

    private val _sideEffects = Channel<MatchContract.SideEffect>()
    val sideEffects = _sideEffects.receiveAsFlow()

    fun onIntent(intent: MatchContract.Intent) {
        when (intent) {
            is MatchContract.Intent.LoadMatch -> { /* TODO: MS-14 */ }
            is MatchContract.Intent.RetryMatch -> { /* TODO: MS-14 */ }
        }
    }
}
