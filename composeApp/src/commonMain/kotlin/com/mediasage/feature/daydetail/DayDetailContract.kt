package com.mediasage.feature.daydetail

object DayDetailContract {

    enum class Tab { MORNING, EVENING }

    data class ReflectionSummary(
        val scriptureReference: String,
        val scriptureText: String,
        val insight: String,
        val implication: String,
        val inspiration: String,
        val tone: String,
    )

    sealed interface UiState {
        data class Ready(
            val epochDay: Long,
            val figureName: String? = null,
            val figureImageUrl: String? = null,
            val selectedTab: Tab = Tab.MORNING,
            val reflections: List<ReflectionSummary> = emptyList(),
        ) : UiState
    }

    sealed interface Intent {
        data class TabSelected(val tab: Tab) : Intent
    }
}
