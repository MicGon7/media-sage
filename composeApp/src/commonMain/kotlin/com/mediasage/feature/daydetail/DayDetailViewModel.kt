package com.mediasage.feature.daydetail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mediasage.domain.model.DailyReflection
import com.mediasage.domain.model.DayDetailData
import com.mediasage.domain.model.Encouragement
import com.mediasage.domain.usecase.GetDayDetailUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

/**
 * Read-only detail for a single day's briefings and saved articles, pushed from
 * [com.mediasage.feature.you.ReaderHistoryScreen]. Tab selection is local-only state combined
 * with the live [GetDayDetailUseCase] stream — the reactive state-holder pattern, since the
 * encouragement list can change while this screen is open (e.g. a bookmark toggled elsewhere).
 */
class DayDetailViewModel(
    private val epochDay: Long,
    private val figureName: String?,
    private val figureImageUrl: String?,
    getDayDetail: GetDayDetailUseCase,
) : ViewModel() {

    private val selectedTab = MutableStateFlow(DayDetailContract.Tab.BRIEFINGS)

    val state: StateFlow<DayDetailContract.UiState> =
        combine(selectedTab, getDayDetail(epochDay)) { tab, data -> buildReady(tab, data) }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
                initialValue = DayDetailContract.UiState.Ready(
                    epochDay = epochDay,
                    figureName = figureName,
                    figureImageUrl = figureImageUrl,
                ),
            )

    fun onIntent(intent: DayDetailContract.Intent) {
        when (intent) {
            is DayDetailContract.Intent.TabSelected -> selectedTab.update { intent.tab }
        }
    }

    private fun buildReady(
        tab: DayDetailContract.Tab,
        data: DayDetailData,
    ): DayDetailContract.UiState.Ready = DayDetailContract.UiState.Ready(
        epochDay = epochDay,
        figureName = figureName,
        figureImageUrl = figureImageUrl,
        selectedTab = tab,
        reflections = listOfNotNull(data.morningReflection, data.eveningReflection).map { it.toSummary() },
        articles = data.encouragements.map { it.toArticleItem() },
    )

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}

private fun DailyReflection.toSummary() = DayDetailContract.ReflectionSummary(
    scriptureReference = scriptureReference,
    scriptureText = scriptureText,
    insight = insight,
    implication = implication,
    inspiration = inspiration,
    tone = tone,
)

private fun Encouragement.toArticleItem() = DayDetailContract.ArticleItem(
    headlineTitle = headlineTitle,
    quoteText = quoteText,
    figureName = figureName,
    figureRole = figureRole,
    figureImageUrl = figureImageUrl,
    articleUrl = articleUrl ?: "",
)
