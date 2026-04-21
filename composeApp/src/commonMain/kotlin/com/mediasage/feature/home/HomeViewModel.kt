package com.mediasage.feature.home

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow

/** ViewModel for the Home/Headlines screen — wired to real data in MS-13. */
class HomeViewModel : ViewModel() {

    private val _state = MutableStateFlow<HomeContract.UiState>(
        HomeContract.UiState.Success(headlines = sampleHeadlines)
    )
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

// Sample data for layout preview — replaced with real API data in MS-13
private val sampleHeadlines = listOf(
    HeadlineItem(
        id = 1,
        title = "Community Gardens Transform Urban Neighborhoods",
        source = "AP News",
        category = "Society",
        snippet = "Local residents report increased connection and purpose through collaborative food growing...",
        imageUrl = null,
        publishedAt = 0L //4 * 3600 * 1000
    ),
    HeadlineItem(
        id = 2,
        title = "Study Shows Power of Gratitude in Mental Wellness",
        source = "NPR",
        category = "Health",
        snippet = "New research reveals daily gratitude practices reduce anxiety and depression by 30%...",
        imageUrl = null,
        publishedAt = 0L //6 * 3600 * 1000
    ),
    HeadlineItem(
        id = 3,
        title = "Bipartisan Bill Addresses Poverty Relief",
        source = "CNN",
        category = "Politics",
        snippet = "Lawmakers from both parties collaborate on sweeping legislation to support vulnerable communities...",
        imageUrl = null,
        publishedAt = 0L //8 * 3600 * 1000
    ),
    HeadlineItem(
        id = 4,
        title = "Ocean Cleanup Project Exceeds Expectations",
        source = "BBC",
        category = "Environment",
        snippet = "Innovative technology removes millions of pounds of plastic, inspiring global movement...",
        imageUrl = null,
        publishedAt = 0L //10 * 3600 * 1000
    ),
    HeadlineItem(
        id = 5,
        title = "Schools Integrate Compassion Into Curriculum",
        source = "USA Today",
        category = "Education",
        snippet = "Districts nationwide adopt empathy and kindness programs with remarkable results...",
        imageUrl = null,
        publishedAt = 0L //12 * 3600 * 1000
    ),
)
