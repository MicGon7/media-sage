package com.mediasage.feature.match

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow

/** ViewModel for the Match screen — wired to real data in MS-14. */
class MatchViewModel : ViewModel() {

    private val _state = MutableStateFlow<MatchContract.UiState>(
        sampleMatchState
    )
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

// Sample data for layout preview — replaced with real API data in MS-14
private val sampleMatchState = MatchContract.UiState.Success(
    headlineTitle = "Community Gardens Transform Urban Neighborhoods",
    headlineSource = "AP News",
    headlineCategory = "Society",
    quoteText = "The glory of God is a human being fully alive, " +
        "and the life of a human being is the vision of God.",
    figureName = "Dietrich Bonhoeffer",
    figureRole = "Theologian & Martyr",
    scriptureReference = "Romans 8:28 — And we know that in all things God works " +
        "for the good of those who love him, who have been called " +
        "according to his purpose.",
    matchExplanation = "This headline about communities coming together to transform " +
        "their neighborhoods through shared purpose echoes Bonhoeffer's belief that " +
        "faith is lived out in community and action, not in isolation.",
    matchTheme = "Community & Purpose",
)
