package com.mediasage.feature.daydetail

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Notes
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.mediasage.ui.FigurePlaceholder
import com.mediasage.ui.MediaSageBackRow
import com.mediasage.ui.SepiaColorFilter
import kotlinx.datetime.LocalDate
import mediasage.composeapp.generated.resources.Res
import mediasage.composeapp.generated.resources.briefing_card_evening
import mediasage.composeapp.generated.resources.briefing_card_morning
import mediasage.composeapp.generated.resources.day_detail_notes_action
import mediasage.composeapp.generated.resources.day_detail_share_action
import mediasage.composeapp.generated.resources.history_empty_day_for
import mediasage.composeapp.generated.resources.history_empty_day_subtitle
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
            when {
                ready.reflections.isEmpty() -> BriefingsEmptyState(ready.epochDay)
                ready.reflections.size == 1 -> SingleReflectionContent(
                    reflection = ready.reflections.first(),
                    figureName = ready.figureName,
                    figureImageUrl = ready.figureImageUrl,
                )
                else -> TabbedReflectionContent(
                    selectedTab = ready.selectedTab,
                    reflections = ready.reflections,
                    figureName = ready.figureName,
                    figureImageUrl = ready.figureImageUrl,
                    onIntent = onIntent,
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

@Composable
private fun DayDetailHeader(epochDay: Long) {
    val dateText = remember(epochDay) { formatEpochDay(epochDay) }
    Text(text = dateText, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
}

@Composable
private fun SingleReflectionContent(
    reflection: DayDetailContract.ReflectionSummary,
    figureName: String?,
    figureImageUrl: String?,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(top = 16.dp)) {
        ToneHeader(tone = reflection.tone)
        Spacer(modifier = Modifier.height(8.dp))
        ReflectionCard(reflection = reflection, figureName = figureName, figureImageUrl = figureImageUrl)
    }
}

@Composable
private fun ToneHeader(tone: String) {
    val toneLabel = if (tone == TONE_MORNING) Res.string.briefing_card_morning else Res.string.briefing_card_evening
    Text(
        text = stringResource(toneLabel),
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(horizontal = 16.dp),
    )
}

@Composable
private fun TabbedReflectionContent(
    selectedTab: DayDetailContract.Tab,
    reflections: List<DayDetailContract.ReflectionSummary>,
    figureName: String?,
    figureImageUrl: String?,
    onIntent: (DayDetailContract.Intent) -> Unit,
) {
    val selectedTone = if (selectedTab == DayDetailContract.Tab.MORNING) TONE_MORNING else "evening"
    val selected = reflections.firstOrNull { it.tone == selectedTone } ?: reflections.first()
    Column(modifier = Modifier.fillMaxWidth()) {
        DayDetailTabRow(selectedTab = selectedTab, onIntent = onIntent)
        ReflectionCard(reflection = selected, figureName = figureName, figureImageUrl = figureImageUrl)
    }
}

@Composable
private fun DayDetailTabRow(
    selectedTab: DayDetailContract.Tab,
    onIntent: (DayDetailContract.Intent) -> Unit,
) {
    val selectedIndex = DayDetailContract.Tab.entries.indexOf(selectedTab)
    TabRow(selectedTabIndex = selectedIndex) {
        Tab(
            selected = selectedTab == DayDetailContract.Tab.MORNING,
            onClick = { onIntent(DayDetailContract.Intent.TabSelected(DayDetailContract.Tab.MORNING)) },
            text = { Text(stringResource(Res.string.briefing_card_morning)) },
        )
        Tab(
            selected = selectedTab == DayDetailContract.Tab.EVENING,
            onClick = { onIntent(DayDetailContract.Intent.TabSelected(DayDetailContract.Tab.EVENING)) },
            text = { Text(stringResource(Res.string.briefing_card_evening)) },
        )
    }
}

@Composable
private fun ReflectionCard(
    reflection: DayDetailContract.ReflectionSummary,
    figureName: String?,
    figureImageUrl: String?,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(220.dp)
                .clip(MaterialTheme.shapes.small),
        ) {
            if (figureImageUrl != null) {
                AsyncImage(
                    model = figureImageUrl,
                    contentDescription = figureName,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                    colorFilter = SepiaColorFilter,
                )
            } else {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surfaceVariant) {
                    Box(contentAlignment = Alignment.Center) {
                        FigurePlaceholder(name = figureName.orEmpty(), size = 80.dp)
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        if (figureName != null) {
            Text(
                text = figureName,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(modifier = Modifier.height(4.dp))
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.primary, thickness = 1.dp)
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = reflection.scriptureReference,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "“${reflection.scriptureText}”",
            style = MaterialTheme.typography.bodyMedium,
            fontStyle = FontStyle.Italic,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = reflection.insight,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = reflection.implication,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = reflection.inspiration,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(12.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, thickness = 0.5.dp)
        ReflectionActions()
    }
}

@Composable
private fun ReflectionActions() {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        ReflectionActionButton(icon = Icons.AutoMirrored.Outlined.Notes, label = stringResource(Res.string.day_detail_notes_action))
        ReflectionActionButton(icon = Icons.Outlined.Share, label = stringResource(Res.string.day_detail_share_action))
    }
}

@Composable
private fun ReflectionActionButton(icon: ImageVector, label: String) {
    Row(
        modifier = Modifier.clickable(onClick = {}),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp),
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(text = label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
