package com.mediasage.feature.you

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.Today
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mediasage.ui.MediaSageBackRow
import com.mediasage.ui.MediaSageBriefingHeader
import com.mediasage.ui.MediaSageDateDivider
import com.mediasage.ui.MediaSageEmptyState
import com.mediasage.ui.MediaSageScriptureBlock
import kotlinx.coroutines.launch
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus
import mediasage.composeapp.generated.resources.Res
import mediasage.composeapp.generated.resources.reader_calendar_next_year
import mediasage.composeapp.generated.resources.reader_calendar_prev_year
import mediasage.composeapp.generated.resources.reader_calendar_today
import mediasage.composeapp.generated.resources.reader_history_intro
import mediasage.composeapp.generated.resources.reader_history_list_empty_subtitle
import mediasage.composeapp.generated.resources.reader_history_list_empty_title
import mediasage.composeapp.generated.resources.reader_history_view_calendar
import mediasage.composeapp.generated.resources.reader_history_view_list
import mediasage.composeapp.generated.resources.reader_history_view_options
import mediasage.composeapp.generated.resources.you_calendar_section_title
import mediasage.composeapp.generated.resources.you_nav_history
import org.jetbrains.compose.resources.stringResource

private const val MONTHS_PER_YEAR = 12

@Composable
fun ReaderHistoryScreen(
    state: ReaderHistoryContract.UiState,
    onIntent: (ReaderHistoryContract.Intent) -> Unit,
    onNavigateBack: () -> Unit = {},
    onNavigateToDayDetail: (epochDay: Long, figureName: String?, figureImageUrl: String?) -> Unit = { _, _, _ -> },
) {
    val ready = state as? ReaderHistoryContract.UiState.Ready

    Surface(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            MediaSageBackRow(onNavigateBack = onNavigateBack) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(Res.string.you_nav_history),
                        style = MaterialTheme.typography.titleLarge,
                    )
                    if (ready != null) {
                        ViewModeMenuButton(viewMode = ready.viewMode, onIntent = onIntent)
                    }
                }
            }
            if (ready != null) {
                Text(
                    text = stringResource(Res.string.reader_history_intro),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
                when (ready.viewMode) {
                    ReaderHistoryContract.ViewMode.CALENDAR -> {
                        Text(
                            text = stringResource(Res.string.you_calendar_section_title),
                            style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.5.sp),
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 8.dp),
                        )
                        HistoryCalendarCarousel(
                            todayEpochDay = ready.todayEpochDay,
                            earliestEpochDay = ready.earliestEpochDay,
                            calendarDays = ready.calendarDays,
                            onIntent = onIntent,
                            onNavigateToDayDetail = onNavigateToDayDetail,
                        )
                    }
                    ReaderHistoryContract.ViewMode.LIST -> HistoryListView(
                        listDays = ready.listDays,
                        onNavigateToDayDetail = onNavigateToDayDetail,
                    )
                }
            }
        }
    }
}

/**
 * A trailing icon button in the top bar that opens a [DropdownMenu] anchored to itself — Compose's
 * equivalent of the near-touch-point popover iOS apps use for a view-mode switch, rather than a
 * second row of tabs competing with the back row for space.
 */
@Composable
private fun ViewModeMenuButton(
    viewMode: ReaderHistoryContract.ViewMode,
    onIntent: (ReaderHistoryContract.Intent) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(
                imageVector = Icons.Filled.MoreVert,
                contentDescription = stringResource(Res.string.reader_history_view_options),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            containerColor = MaterialTheme.colorScheme.surface,
            tonalElevation = 0.dp,
        ) {
            ReaderHistoryContract.ViewMode.entries.forEach { option ->
                DropdownMenuItem(
                    text = { Text(stringResource(option.labelRes())) },
                    onClick = {
                        onIntent(ReaderHistoryContract.Intent.ViewModeChanged(option))
                        expanded = false
                    },
                    leadingIcon = {
                        if (option == viewMode) {
                            Icon(imageVector = Icons.Filled.Check, contentDescription = null)
                        }
                    },
                )
            }
        }
    }
}

private fun ReaderHistoryContract.ViewMode.labelRes() = when (this) {
    ReaderHistoryContract.ViewMode.CALENDAR -> Res.string.reader_history_view_calendar
    ReaderHistoryContract.ViewMode.LIST -> Res.string.reader_history_view_list
}

@Composable
private fun HistoryCalendarCarousel(
    todayEpochDay: Long,
    earliestEpochDay: Long,
    calendarDays: List<ReaderHistoryContract.CalendarDay>,
    onIntent: (ReaderHistoryContract.Intent) -> Unit,
    onNavigateToDayDetail: (epochDay: Long, figureName: String?, figureImageUrl: String?) -> Unit,
) {
    val todayDate = remember(todayEpochDay) { LocalDate.fromEpochDays(todayEpochDay.toInt()) }
    val earliestDate = remember(earliestEpochDay) { LocalDate.fromEpochDays(earliestEpochDay.toInt()) }
    val baseMonthIndex = remember(earliestDate) { earliestDate.year * MONTHS_PER_YEAR + (earliestDate.monthNumber - 1) }
    val todayMonthIndex = remember(todayDate) { todayDate.year * MONTHS_PER_YEAR + (todayDate.monthNumber - 1) }
    val totalMonths = remember(baseMonthIndex, todayMonthIndex) { todayMonthIndex - baseMonthIndex + 1 }
    val initialPage = remember(totalMonths) { totalMonths - 1 }
    val pagerState = rememberPagerState(initialPage = initialPage) { totalMonths }
    val coroutineScope = rememberCoroutineScope()
    val currentYear = remember(baseMonthIndex, pagerState.currentPage) {
        (baseMonthIndex + pagerState.currentPage) / MONTHS_PER_YEAR
    }
    val loadedPage = remember(calendarDays) { epochDayToPage(calendarDays, baseMonthIndex, initialPage) }
    val pageCache = remember { mutableStateMapOf<Int, List<ReaderHistoryContract.CalendarDay>>() }

    LaunchedEffect(calendarDays) {
        if (calendarDays.isNotEmpty()) pageCache[loadedPage] = calendarDays
    }

    LaunchedEffect(pagerState.settledPage) {
        val absoluteIndex = baseMonthIndex + pagerState.settledPage
        onIntent(
            ReaderHistoryContract.Intent.MonthPageChanged(
                year = absoluteIndex / MONTHS_PER_YEAR,
                month = absoluteIndex % MONTHS_PER_YEAR + 1,
            ),
        )
    }

    Column {
        YearSelector(
            year = currentYear,
            prevEnabled = pagerState.currentPage > 0,
            nextEnabled = pagerState.currentPage < totalMonths - 1,
            prevYearDescription = stringResource(Res.string.reader_calendar_prev_year),
            nextYearDescription = stringResource(Res.string.reader_calendar_next_year),
            onPrevYear = {
                val target = (pagerState.currentPage - MONTHS_PER_YEAR).coerceAtLeast(0)
                coroutineScope.launch { pagerState.animateScrollToPage(target) }
            },
            onNextYear = {
                val target = (pagerState.currentPage + MONTHS_PER_YEAR).coerceAtMost(totalMonths - 1)
                coroutineScope.launch { pagerState.animateScrollToPage(target) }
            },
            onToday = { coroutineScope.launch { pagerState.animateScrollToPage(initialPage) } },
        )
        HorizontalPager(state = pagerState) { page ->
            val daysForPage = pageCache[page] ?: buildSkeletonDays(page, baseMonthIndex, todayEpochDay)
            CalendarCard(
                days = daysForPage,
                onDayTapped = { epochDay ->
                    val day = daysForPage.find { it.epochDay == epochDay }
                    if (day?.isFuture != true) {
                        onNavigateToDayDetail(epochDay, day?.figureName, day?.figurePortraitUrl)
                    }
                },
                modifier = Modifier.padding(bottom = 16.dp),
            )
        }
    }
}

@Composable
private fun YearSelector(
    year: Int,
    prevEnabled: Boolean,
    nextEnabled: Boolean,
    prevYearDescription: String,
    nextYearDescription: String,
    onPrevYear: () -> Unit,
    onNextYear: () -> Unit,
    onToday: () -> Unit,
) {
    Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
        Row(
            modifier = Modifier.align(Alignment.Center),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(
                onClick = onPrevYear,
                enabled = prevEnabled,
                modifier = Modifier.alpha(if (prevEnabled) 1f else 0f),
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = prevYearDescription,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = year.toString(),
                style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 1.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 8.dp),
            )
            IconButton(
                onClick = onNextYear,
                enabled = nextEnabled,
                modifier = Modifier.alpha(if (nextEnabled) 1f else 0f),
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = nextYearDescription,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        IconButton(
            onClick = onToday,
            modifier = Modifier.align(Alignment.CenterEnd),
        ) {
            Icon(
                imageVector = Icons.Outlined.Today,
                contentDescription = stringResource(Res.string.reader_calendar_today),
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun HistoryListView(
    listDays: List<ReaderHistoryContract.ListDay>,
    onNavigateToDayDetail: (epochDay: Long, figureName: String?, figureImageUrl: String?) -> Unit,
) {
    if (listDays.isEmpty()) {
        MediaSageEmptyState(
            title = stringResource(Res.string.reader_history_list_empty_title),
            subtitle = stringResource(Res.string.reader_history_list_empty_subtitle),
        )
        return
    }
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        items(listDays, key = { it.epochDay }) { day ->
            HistoryDayCard(
                day = day,
                onClick = { onNavigateToDayDetail(day.epochDay, day.figureName, day.figurePortraitUrl) },
            )
            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 16.dp),
                color = MaterialTheme.colorScheme.outlineVariant,
                thickness = 0.5.dp,
            )
        }
    }
}

@Composable
private fun HistoryDayCard(
    day: ReaderHistoryContract.ListDay,
    onClick: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        MediaSageDateDivider(dateLabel = formatListDate(day.epochDay))
        MediaSageBriefingHeader(
            figureName = day.figureName,
            figureImageUrl = day.figurePortraitUrl,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp),
        )
        if (day.scriptureReference != null && day.scriptureText != null) {
            MediaSageScriptureBlock(
                scriptureReference = day.scriptureReference,
                scriptureText = day.scriptureText,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 12.dp),
            )
        } else {
            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}

private fun formatListDate(epochDay: Long): String {
    val date = LocalDate.fromEpochDays(epochDay.toInt())
    val monthName = date.month.name.lowercase().replaceFirstChar { it.titlecase() }
    return "$monthName ${date.dayOfMonth}, ${date.year}"
}

private fun buildSkeletonDays(
    page: Int,
    baseMonthIndex: Int,
    todayEpochDay: Long,
): List<ReaderHistoryContract.CalendarDay> {
    val absoluteIndex = baseMonthIndex + page
    val monthStart = LocalDate(absoluteIndex / MONTHS_PER_YEAR, absoluteIndex % MONTHS_PER_YEAR + 1, 1)
    val monthStartEpoch = monthStart.toEpochDays().toLong()
    val daysInMonth = monthStart.plus(1, DateTimeUnit.MONTH).toEpochDays() - monthStart.toEpochDays()
    return (0 until daysInMonth.toInt()).map { d ->
        val epochDay = monthStartEpoch + d
        ReaderHistoryContract.CalendarDay(
            epochDay = epochDay,
            dateNumber = LocalDate.fromEpochDays(epochDay.toInt()).dayOfMonth,
            isToday = epochDay == todayEpochDay,
            isFuture = epochDay > todayEpochDay,
            hasData = false,
            figurePortraitUrl = null,
            figureName = null,
        )
    }
}

private fun epochDayToPage(
    calendarDays: List<ReaderHistoryContract.CalendarDay>,
    baseMonthIndex: Int,
    fallback: Int,
): Int {
    val epochDay = calendarDays.firstOrNull()?.epochDay ?: return fallback
    val date = LocalDate.fromEpochDays(epochDay.toInt())
    return date.year * MONTHS_PER_YEAR + (date.monthNumber - 1) - baseMonthIndex
}
