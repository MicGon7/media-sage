package com.mediasage.feature.daydetail

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
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
import com.mediasage.ui.MediaSageBriefingBody
import com.mediasage.ui.MediaSageBriefingCard
import com.mediasage.ui.MediaSageBriefingHeader
import com.mediasage.ui.ThemeChip
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
            Column(modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
                when {
                    ready.briefings.isEmpty() -> BriefingsEmptyState(ready.epochDay)
                    ready.briefings.size == 1 -> SingleBriefingContent(
                        briefing = ready.briefings.first(),
                        figureName = ready.figureName,
                        figureImageUrl = ready.figureImageUrl,
                    )

                    else -> MultipleBriefingsContent(
                        briefings = ready.briefings,
                        expandedTone = ready.expandedTone,
                        figureName = ready.figureName,
                        figureImageUrl = ready.figureImageUrl,
                        onIntent = onIntent,
                    )
                }
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

/**
 * The figure's portrait and name never change between a day's morning and evening briefing, so
 * [MediaSageBriefingHeader] renders once above both. The two briefings form an accordion — at most
 * one is expanded at a time, so opening one collapses the other — and only [MediaSageBriefingBody]
 * (theme, sources, scripture, and the reflection text) toggles per section.
 */
@Composable
private fun MultipleBriefingsContent(
    briefings: List<DayDetailContract.BriefingSummary>,
    expandedTone: String?,
    figureName: String?,
    figureImageUrl: String?,
    onIntent: (DayDetailContract.Intent) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
        MediaSageBriefingHeader(figureName = figureName, figureImageUrl = figureImageUrl)
        Spacer(modifier = Modifier.height(8.dp))
        briefings.forEach { briefing ->
            ExpandableBriefingSection(
                briefing = briefing,
                expanded = briefing.tone == expandedTone,
                onToggle = { onIntent(DayDetailContract.Intent.BriefingToggled(briefing.tone)) },
            )
        }
    }
}

@Composable
private fun ExpandableBriefingSection(
    briefing: DayDetailContract.BriefingSummary,
    expanded: Boolean,
    onToggle: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().clickable(onClick = onToggle)
                .padding(vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(toneLabelRes(briefing.tone)),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                if (briefing.theme != null) {
                    Spacer(modifier = Modifier.width(8.dp))
                    ThemeChip(theme = briefing.theme)
                }
            }
            Icon(
                imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        AnimatedVisibility(visible = expanded) {
            MediaSageBriefingBody(
                scriptureReference = briefing.scriptureReference,
                scriptureText = briefing.scriptureText,
                insight = briefing.insight,
                implication = briefing.implication,
                inspiration = briefing.inspiration,
                sources = briefing.sources,
            )
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, thickness = 0.5.dp)
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
