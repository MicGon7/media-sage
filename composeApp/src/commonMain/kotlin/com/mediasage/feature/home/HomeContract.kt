package com.mediasage.feature.home

import com.mediasage.ui.ErrorType

/** MVI contract for the Home/Headlines feature. */
object HomeContract {

    sealed interface UiState {
        data object Loading : UiState
        data class Success(
            val headlines: List<HeadlineItem>,
            val isRefreshing: Boolean = false,
            val briefingCard: BriefingCardState = BriefingCardState.Hidden
        ) : UiState
        data class Error(val errorType: ErrorType) : UiState
    }

    sealed interface BriefingCardState {
        data object Hidden : BriefingCardState
        data object Loading : BriefingCardState
        data class Ready(
            val figureId: Long,
            val figureName: String,
            val figureImageUrl: String?,
            val scriptureReference: String,
            val scriptureText: String,
            val reflection: String,
            val sources: List<String>,
            val tone: String
        ) : BriefingCardState
    }

    sealed interface Intent {
        data object LoadHeadlines : Intent
        data object RefreshHeadlines : Intent
        data class HeadlineClicked(val articleUrl: String) : Intent
    }

    sealed interface SideEffect {
        data class NavigateToDetail(val articleUrl: String) : SideEffect
        data class ShowError(val message: String) : SideEffect
    }
}

data class HeadlineItem(
    val id: Long,
    val articleUrl: String,
    val title: String,
    val source: String,
    val category: String = "",
    val snippet: String = "",
    val imageUrl: String?,
    val publishedAt: Long = 0L
)
