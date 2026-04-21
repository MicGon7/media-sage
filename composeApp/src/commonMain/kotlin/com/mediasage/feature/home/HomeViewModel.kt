package com.mediasage.feature.home

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow

/** ViewModel for the Home/Headlines screen — implementation in MS-13. */
class HomeViewModel : ViewModel() {

    private val _state = MutableStateFlow<HomeContract.UiState>(HomeContract.UiState.Loading)
    val state: StateFlow<HomeContract.UiState> = _state.asStateFlow()

    private val _sideEffects = Channel<HomeContract.SideEffect>()
    val sideEffects = _sideEffects.receiveAsFlow()

    fun onIntent(intent: HomeContract.Intent) {
        when (intent) {
            is HomeContract.Intent.LoadHeadlines -> { /* TODO: MS-13 */ }
            is HomeContract.Intent.RefreshHeadlines -> { /* TODO: MS-13 */ }
            is HomeContract.Intent.HeadlineClicked -> { /* Handled via navigation callback */ }
        }
    }
}
