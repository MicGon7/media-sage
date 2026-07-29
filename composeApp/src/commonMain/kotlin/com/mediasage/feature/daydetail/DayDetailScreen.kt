package com.mediasage.feature.daydetail

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mediasage.ui.MediaSageBackRow
import com.mediasage.ui.MediaSageBriefingCard
import com.mediasage.ui.MediaSageTabRow
import kotlinx.datetime.LocalDate
import mediasage.composeapp.generated.resources.Res
import mediasage.composeapp.generated.resources.briefing_card_evening
import mediasage.composeapp.generated.resources.briefing_card_morning
import mediasage.composeapp.generated.resources.history_empty_day_for
import mediasage.composeapp.generated.resources.history_empty_day_subtitle
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

private const val TONE_MORNING = "morning"

@Composable
fun DayDetailScreen(
    state: DayDetailContract.UiState,
    onIntent: (DayDetailContract.Intent) -> Unit,
    onNavigateBack: () -> Unit = {},
) {
    val ready = state as? DayDetailContract.UiState.Ready ?: return
    Surface(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            MediaSageBackRow(onNavigateBack = onNavigateBack) {
                DayDetailHeader(epochDay = ready.epochDay)
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
            ) {
                when {
                    ready.briefings.isEmpty() -> BriefingsEmptyState(ready.epochDay)
                    else -> SingleBriefingContent(
                        briefing = ready.briefings.firstOrNull { it.tone == ready.selectedTone }
                            ?: ready.briefings.first(),
                        figureName = ready.figureName,
                        figureImageUrl = ready.figureImageUrl,
                    )
                }
            }
            if (ready.briefings.size > 1) {
                MediaSageTabRow(
                    selectedIndex = ready.briefings.indexOfFirst { it.tone == ready.selectedTone }.coerceAtLeast(0),
                    tabLabels = ready.briefings.map { stringResource(toneLabelRes(it.tone)) },
                    onTabSelected = { index ->
                        onIntent(DayDetailContract.Intent.BriefingToneSelected(ready.briefings[index].tone))
                    },
                )
            }
        }
    }
}

private fun formatEpochDay(epochDay: Long): String {
    val date = LocalDate.fromEpochDays(epochDay.toInt())
    val day = date.dayOfWeek.name.lowercase().replaceFirstChar { it.uppercase() }
    val month = date.month.name.lowercase().replaceFirstChar { it.uppercase() }
    return "$day, $month ${date.dayOfMonth}"
}

private fun toneLabelRes(tone: String): StringResource =
    if (tone == TONE_MORNING) Res.string.briefing_card_morning else Res.string.briefing_card_evening

@Composable
private fun DayDetailHeader(epochDay: Long) {
    val dateText = remember(epochDay) { formatEpochDay(epochDay) }
    Text(
        text = dateText,
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.SemiBold
    )
}

@Composable
private fun SingleBriefingContent(
    briefing: DayDetailContract.BriefingSummary,
    figureName: String?,
    figureImageUrl: String?,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        MediaSageBriefingCard(
            figureName = figureName,
            figureImageUrl = figureImageUrl,
            scriptureReference = briefing.scriptureReference,
            scriptureText = briefing.scriptureText,
            insight = briefing.insight,
            implication = briefing.implication,
            inspiration = briefing.inspiration,
            theme = briefing.theme,
            sources = briefing.sources,
        )
    }
}

@Composable
private fun BriefingsEmptyState(epochDay: Long) {
    val dateText = remember(epochDay) { formatEpochDay(epochDay) }
    Column(
        modifier = Modifier.fillMaxWidth().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(Res.string.history_empty_day_for, dateText),
            style = MaterialTheme.typography.titleSmall,
        )
        Text(
            text = stringResource(Res.string.history_empty_day_subtitle),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}
