package com.mediasage.feature.daydetail

object DayDetailContract {

    data class BriefingSummary(
        val scriptureReference: String,
        val scriptureText: String,
        val insight: String,
        val implication: String,
        val inspiration: String,
        val sources: List<String>,
        val tone: String,
        val theme: String? = null,
    )

    sealed interface UiState {
        data class Ready(
            val epochDay: Long,
            val figureName: String? = null,
            val figureImageUrl: String? = null,
            val expandedTone: String? = null,
            val briefings: List<BriefingSummary> = emptyList(),
        ) : UiState
    }

    sealed interface Intent {
        data class BriefingToggled(val tone: String) : Intent
    }
}
