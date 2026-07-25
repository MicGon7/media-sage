package com.mediasage.feature.daydetail

object DayDetailContract {

    enum class Tab { BRIEFINGS, ARTICLES }

    data class ReflectionSummary(
        val scriptureReference: String,
        val scriptureText: String,
        val insight: String,
        val implication: String,
        val inspiration: String,
        val tone: String,
    )

    data class ArticleItem(
        val headlineTitle: String,
        val quoteText: String,
        val figureName: String,
        val figureRole: String,
        val figureImageUrl: String?,
        val articleUrl: String,
    )

    sealed interface UiState {
        data class Ready(
            val epochDay: Long,
            val figureName: String? = null,
            val figureImageUrl: String? = null,
            val selectedTab: Tab = Tab.BRIEFINGS,
            val reflections: List<ReflectionSummary> = emptyList(),
            val articles: List<ArticleItem> = emptyList(),
        ) : UiState
    }

    sealed interface Intent {
        data class TabSelected(val tab: Tab) : Intent
    }
}
