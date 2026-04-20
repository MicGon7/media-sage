package com.mediasage.feature.detail

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow

/** ViewModel for the Match Detail screen — implementation in MS-14. */
class DetailViewModel : ViewModel() {

    private val _state = MutableStateFlow<DetailContract.UiState>(DetailContract.UiState.Loading)
    val state: StateFlow<DetailContract.UiState> = _state.asStateFlow()

    private val _sideEffects = Channel<DetailContract.SideEffect>()
    val sideEffects = _sideEffects.receiveAsFlow()

    fun onIntent(intent: DetailContract.Intent) {
        when (intent) {
            is DetailContract.Intent.LoadMatch -> { /* TODO: MS-14 */ }
            is DetailContract.Intent.RetryMatch -> { /* TODO: MS-14 */ }
        }
    }
}
