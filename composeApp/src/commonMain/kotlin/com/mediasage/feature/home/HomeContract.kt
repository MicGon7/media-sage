package com.mediasage.feature.home

/** MVI contract for the Home/Headlines feature. */
object HomeContract {

    sealed interface UiState {
        data object Loading : UiState
        data class Success(
            val headlines: List<HeadlineItem>,
            val isRefreshing: Boolean = false
        ) : UiState
        data class Error(val message: String) : UiState
    }

    sealed interface Intent {
        data object LoadHeadlines : Intent
        data object RefreshHeadlines : Intent
        data class HeadlineClicked(val headlineId: Long) : Intent
    }

    sealed interface SideEffect {
        data class NavigateToDetail(val headlineId: Long) : SideEffect
        data class ShowError(val message: String) : SideEffect
    }
}

data class HeadlineItem(
    val id: Long,
    val title: String,
    val source: String,
    val category: String = "",
    val snippet: String = "",
    val imageUrl: String?,
    val publishedAt: Long = 0L
)
