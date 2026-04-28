package com.mediasage.feature.you

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class YouViewModel : ViewModel() {

    private val _state = MutableStateFlow<YouContract.UiState>(YouContract.UiState.Ready)
    val state: StateFlow<YouContract.UiState> = _state.asStateFlow()

    fun onIntent(intent: YouContract.Intent) { /* shell — no intents yet */ }
}
