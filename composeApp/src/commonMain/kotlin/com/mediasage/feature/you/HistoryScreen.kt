package com.mediasage.feature.you

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.mediasage.theme.BrandAmber
import com.mediasage.ui.MediaSageHeadlineCard
import com.mediasage.ui.MediaSageLoadingState
import kotlinx.datetime.LocalDate
import mediasage.composeapp.generated.resources.Res
import mediasage.composeapp.generated.resources.history_empty_day_for
import mediasage.composeapp.generated.resources.history_empty_day_subtitle
import mediasage.composeapp.generated.resources.briefing_card_based_on
import mediasage.composeapp.generated.resources.history_tab_articles
import mediasage.composeapp.generated.resources.history_tab_briefing
import mediasage.composeapp.generated.resources.history_mode_month
import mediasage.composeapp.generated.resources.history_mode_week
import mediasage.composeapp.generated.resources.history_mode_year
import mediasage.composeapp.generated.resources.nav_back
import mediasage.composeapp.generated.resources.title_history
import org.jetbrains.compose.resources.stringResource

@Composable
fun HistoryScreen(
    state: HistoryContract.UiState,
    onIntent: (HistoryContract.Intent) -> Unit,
    onNavigateBack: () -> Unit = {},
    onNavigateToDetail: (String) -> Unit = {},
) {
    Surface(modifier = Modifier.fillMaxSize()) {
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            item {
                HistoryHeader(
                    selectedEpochDay = (state as? HistoryContract.UiState.Ready)?.selectedEpochDay,
                    onNavigateBack = onNavigateBack,
                )
            }
            when (state) {
                is HistoryContract.UiState.Loading -> item { MediaSageLoadingState() }
                is HistoryContract.UiState.Ready -> renderDayDetail(
                    state.selectedEpochDay,
                    state.dayDetail,
                    state.selectedTab,
                    onIntent,
                    onNavigateToDetail,
                )
            }
        }
    }
}

@Composable
private fun HistoryHeader(
    selectedEpochDay: Long?,
    onNavigateBack: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onNavigateBack) {
                Icon(
                    Icons.AutoMirrored.Outlined.ArrowBack,
                    contentDescription = stringResource(Res.string.nav_back),
                )
            }
            Text(
                text = if (selectedEpochDay != null) epochDayToName(selectedEpochDay)
                       else stringResource(Res.string.title_history),
                style = MaterialTheme.typography.titleMedium,
            )
        }
        HorizontalDivider()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ModeSelector(
    selected: HistoryContract.CalendarMode,
    onSelected: (HistoryContract.CalendarMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    val modes = HistoryContract.CalendarMode.entries
    SingleChoiceSegmentedButtonRow(modifier = modifier) {
        modes.forEachIndexed { i, mode ->
            SegmentedButton(
                selected = mode == selected,
                onClick = { onSelected(mode) },
                shape = SegmentedButtonDefaults.itemShape(index = i, count = modes.size),
                colors = SegmentedButtonDefaults.colors(
                    activeContainerColor = BrandAmber.copy(alpha = 0.2f),
                    activeContentColor = MaterialTheme.colorScheme.onSurface,
                    activeBorderColor = BrandAmber,
                    inactiveBorderColor = MaterialTheme.colorScheme.outline,
                ),
                label = {
                    Text(
                        text = when (mode) {
                            HistoryContract.CalendarMode.WEEK -> stringResource(Res.string.history_mode_week)
                            HistoryContract.CalendarMode.MONTH -> stringResource(Res.string.history_mode_month)
                            HistoryContract.CalendarMode.YEAR -> stringResource(Res.string.history_mode_year)
                        }
                    )
                },
            )
        }
    }
}

@Composable
private fun CalendarSection(
    days: List<HistoryContract.CalendarDay>,
    mode: HistoryContract.CalendarMode,
    selectedEpochDay: Long?,
    onDayTapped: (Long) -> Unit,
) {
    when (mode) {
        HistoryContract.CalendarMode.WEEK ->
            WeekCalendarRow(days, selectedEpochDay, onDayTapped, Modifier.padding(vertical = 8.dp))

        HistoryContract.CalendarMode.MONTH ->
            MonthCalendarGrid(days, selectedEpochDay, onDayTapped)

        HistoryContract.CalendarMode.YEAR ->
            YearCalendarGrid(days, selectedEpochDay, onDayTapped)
    }
}

@Composable
private fun WeekCalendarRow(
    days: List<HistoryContract.CalendarDay>,
    selectedEpochDay: Long?,
    onDayTapped: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        days.forEach { day ->
            CalendarCell(
                day = day,
                isSelected = day.epochDay == selectedEpochDay,
                onClick = { onDayTapped(day.epochDay) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun MonthCalendarGrid(
    days: List<HistoryContract.CalendarDay>,
    selectedEpochDay: Long?,
    onDayTapped: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val firstDayOffset = days.firstOrNull()?.let {
        LocalDate.fromEpochDays(it.epochDay.toInt()).dayOfWeek.ordinal
    } ?: 0
    Column(modifier = modifier.padding(8.dp)) {
        WeekDayHeaders()
        splitIntoWeeks(firstDayOffset, days).forEach { week ->
            Row(modifier = Modifier.fillMaxWidth()) {
                week.forEach { day ->
                    if (day == null) {
                        Box(modifier = Modifier.weight(1f).padding(4.dp).size(32.dp))
                    } else {
                        CalendarCell(
                            day,
                            day.epochDay == selectedEpochDay,
                            { onDayTapped(day.epochDay) },
                            Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun YearCalendarGrid(
    tiles: List<HistoryContract.CalendarDay>,
    selectedEpochDay: Long?,
    onTileTapped: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.padding(8.dp)) {
        tiles.chunked(3).forEach { row ->
            Row(modifier = Modifier.fillMaxWidth()) {
                row.forEach { tile ->
                    MonthTile(
                        tile,
                        tile.epochDay == selectedEpochDay,
                        { onTileTapped(tile.epochDay) },
                        Modifier.weight(1f)
                    )
                }
                repeat(3 - row.size) { Box(modifier = Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun CalendarCell(
    day: HistoryContract.CalendarDay,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(4.dp).clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text = day.label,
            style = MaterialTheme.typography.labelSmall,
            color = when {
                day.isToday -> BrandAmber
                day.hasData || isSelected -> MaterialTheme.colorScheme.onSurface
                else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
            },
            fontWeight = if (day.isToday) FontWeight.Bold else FontWeight.Normal,
        )
        HistoryDayPortrait(day, isSelected)
    }
}

@Composable
private fun HistoryDayPortrait(day: HistoryContract.CalendarDay, isSelected: Boolean) {
    val ring = if (isSelected) Modifier.border(2.dp, BrandAmber, CircleShape) else Modifier
    when {
        day.figurePortraitUrl != null -> AsyncImage(
            model = day.figurePortraitUrl,
            contentDescription = day.figureName,
            modifier = Modifier.size(28.dp).clip(CircleShape).then(ring),
            contentScale = ContentScale.Crop,
            alignment = Alignment.TopCenter,
        )
        day.hasData -> Box(
            modifier = Modifier.size(28.dp).clip(CircleShape)
                .background(BrandAmber.copy(alpha = 0.25f)).then(ring),
            contentAlignment = Alignment.Center,
        ) { Text("†", fontSize = 12.sp, color = BrandAmber) }
        day.isToday -> Box(Modifier.size(6.dp).clip(CircleShape).background(BrandAmber))
        else -> Spacer(Modifier.size(28.dp))
    }
}

@Composable
private fun MonthTile(
    tile: HistoryContract.CalendarDay,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = MaterialTheme.shapes.medium
    val containerColor = when {
        isSelected -> BrandAmber.copy(alpha = 0.25f)
        tile.hasData -> BrandAmber.copy(alpha = 0.1f)
        else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
    }
    Box(
        modifier = modifier
            .padding(6.dp)
            .clip(shape)
            .background(containerColor)
            .then(if (isSelected) Modifier.border(2.dp, BrandAmber, shape) else Modifier)
            .clickable(onClick = onClick)
            .padding(vertical = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = tile.label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = if (tile.hasData || isSelected) 1f else 0.5f),
        )
    }
}

@Composable
private fun WeekDayHeaders() {
    Row(modifier = Modifier.fillMaxWidth()) {
        listOf("M", "T", "W", "T", "F", "S", "S").forEach { label ->
            Box(modifier = Modifier.weight(1f).padding(4.dp), contentAlignment = Alignment.Center) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ReflectionCard(dayDetail: HistoryContract.DayDetail) {
    val reflection = dayDetail.reflection ?: return
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        if (dayDetail.figureName != null) {
            ReflectionByline(dayDetail.figureName, dayDetail.figureImageUrl, reflection.sources)
            Spacer(Modifier.height(8.dp))
            HorizontalDivider()
            Spacer(Modifier.height(8.dp))
        }
        Text(
            text = reflection.scriptureReference,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = "“${reflection.scriptureText}”",
            style = MaterialTheme.typography.bodyMedium,
            fontStyle = FontStyle.Italic,
        )
        Spacer(Modifier.height(8.dp))
        Text(reflection.insight, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(8.dp))
        Text(reflection.implication, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(8.dp))
        Text(reflection.inspiration, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ReflectionByline(name: String, imageUrl: String?, sources: List<String>) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (imageUrl != null) {
            AsyncImage(
                model = imageUrl,
                contentDescription = name,
                modifier = Modifier.size(36.dp).clip(CircleShape),
                contentScale = ContentScale.Crop,
            )
            Spacer(Modifier.width(12.dp))
        }
        Column {
            Text(name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            if (sources.isNotEmpty()) {
                Text(
                    text = "${stringResource(Res.string.briefing_card_based_on)} ${sources.joinToString(", ")}",
                    style = MaterialTheme.typography.labelSmall,
                    fontStyle = FontStyle.Italic,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun EncouragementHistoryCard(
    item: HistoryContract.EncouragementItem,
    onClick: () -> Unit,
    onToggleBookmark: () -> Unit,
) {
    MediaSageHeadlineCard(
        imageUrl = null,
        headlineTitle = item.headlineTitle,
        figureName = item.figureName,
        figureRole = item.figureRole,
        quotePreview = item.quoteText.take(QUOTE_PREVIEW_LENGTH),
        isBookmarked = item.isBookmarked,
        grayscaleImage = true,
        onClick = onClick,
        onBookmarkClick = onToggleBookmark,
    )
}

@Composable
private fun HistoryEmptyDay(dayName: String) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = stringResource(Res.string.history_empty_day_for, dayName),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = stringResource(Res.string.history_empty_day_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DayTabRow(
    selectedTab: HistoryContract.DayTab,
    onTabSelected: (HistoryContract.DayTab) -> Unit,
) {
    val tabs = HistoryContract.DayTab.entries
    SecondaryTabRow(
        selectedTabIndex = tabs.indexOf(selectedTab),
        contentColor = BrandAmber,
    ) {
        tabs.forEach { tab ->
            Tab(
                selected = tab == selectedTab,
                onClick = { onTabSelected(tab) },
                text = {
                    Text(
                        text = when (tab) {
                            HistoryContract.DayTab.BRIEFING -> stringResource(Res.string.history_tab_briefing)
                            HistoryContract.DayTab.ARTICLES -> stringResource(Res.string.history_tab_articles)
                        },
                        style = MaterialTheme.typography.labelMedium,
                    )
                },
            )
        }
    }
}

private fun LazyListScope.renderDayDetail(
    selectedEpochDay: Long?,
    dayDetail: HistoryContract.DayDetail?,
    selectedTab: HistoryContract.DayTab,
    onIntent: (HistoryContract.Intent) -> Unit,
    onNavigateToDetail: (String) -> Unit,
) {
    selectedEpochDay ?: return
    if (dayDetail == null) {
        item { MediaSageLoadingState() }
        return
    }
    item {
        DayTabRow(
            selectedTab,
            onTabSelected = { onIntent(HistoryContract.Intent.SelectTab(it)) })
    }
    item { Spacer(Modifier.height(16.dp)) }
    when (selectedTab) {
        HistoryContract.DayTab.BRIEFING -> {
            if (dayDetail.reflection != null) {
                item { ReflectionCard(dayDetail) }
            } else {
                item { HistoryEmptyDay(epochDayToName(selectedEpochDay)) }
            }
        }

        HistoryContract.DayTab.ARTICLES ->
            renderEncouragements(
                dayDetail.encouragements,
                selectedEpochDay,
                onIntent,
                onNavigateToDetail
            )
    }
}

private fun LazyListScope.renderEncouragements(
    encouragements: List<HistoryContract.EncouragementItem>,
    selectedEpochDay: Long,
    onIntent: (HistoryContract.Intent) -> Unit,
    onNavigateToDetail: (String) -> Unit,
) {
    if (encouragements.isEmpty()) {
        item { HistoryEmptyDay(epochDayToName(selectedEpochDay)) }
        return
    }
    items(encouragements, key = { it.articleUrl }) { enc ->
        EncouragementHistoryCard(
            item = enc,
            onClick = { if (enc.articleUrl.isNotEmpty()) onNavigateToDetail(enc.articleUrl) },
            onToggleBookmark = { onIntent(HistoryContract.Intent.ToggleBookmark(enc.articleUrl)) },
        )
    }
}

private fun epochDayToName(epochDay: Long): String {
    val date = LocalDate.fromEpochDays(epochDay.toInt())
    val dayName = date.dayOfWeek.name.lowercase().replaceFirstChar { it.uppercase() }
    val monthName = date.month.name.lowercase().replaceFirstChar { it.uppercase() }
    return "$dayName, $monthName ${date.day}, ${date.year}"
}

private fun splitIntoWeeks(
    firstDayOffset: Int,
    days: List<HistoryContract.CalendarDay>,
): List<List<HistoryContract.CalendarDay?>> {
    val cells = mutableListOf<HistoryContract.CalendarDay?>()
    repeat(firstDayOffset) { cells.add(null) }
    cells.addAll(days)
    while (cells.size % 7 != 0) cells.add(null)
    return cells.chunked(7)
}

private const val QUOTE_PREVIEW_LENGTH = 120
