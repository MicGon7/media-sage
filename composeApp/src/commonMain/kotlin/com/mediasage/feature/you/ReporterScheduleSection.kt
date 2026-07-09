package com.mediasage.feature.you

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.mutableStateMapOf
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus
import mediasage.composeapp.generated.resources.Res
import mediasage.composeapp.generated.resources.you_reporters_this_month
import mediasage.composeapp.generated.resources.you_reporters_this_week
import mediasage.composeapp.generated.resources.you_toggle_month_view
import mediasage.composeapp.generated.resources.you_toggle_week_view
import mediasage.composeapp.generated.resources.reader_calendar_next_year
import mediasage.composeapp.generated.resources.reader_calendar_prev_year
import org.jetbrains.compose.resources.stringResource

private const val CAROUSEL_START_YEAR_OFFSET = 3

@Composable
fun ReporterScheduleSection(
    weekSlots: List<ReaderContract.DaySlot>,
    calendarDays: List<ReaderContract.CalendarDay>,
    isExpanded: Boolean,
    onIntent: (ReaderContract.Intent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val weekEpochDays = remember(weekSlots) { weekSlots.map { it.epochDay }.toSet() }
    val todayIndex = remember(weekSlots) { weekSlots.indexOfFirst { it.isToday } }
    val rowState = rememberLazyListState(initialFirstVisibleItemIndex = maxOf(0, todayIndex))
    val density = LocalDensity.current
    CenterTodayEffect(rowState, todayIndex, density)

    Column(modifier = modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = if (isExpanded) stringResource(Res.string.you_reporters_this_month)
                       else stringResource(Res.string.you_reporters_this_week),
                style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.5.sp),
                color = MaterialTheme.colorScheme.primary,
            )
            SuggestionChip(
                onClick = { onIntent(ReaderContract.Intent.ToggleCalendarExpanded) },
                label = {
                    Text(
                        if (isExpanded) stringResource(Res.string.you_toggle_week_view)
                        else stringResource(Res.string.you_toggle_month_view),
                    )
                },
                icon = {
                    Icon(
                        imageVector = if (isExpanded) Icons.Filled.KeyboardArrowUp
                                      else Icons.Filled.KeyboardArrowDown,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                    )
                },
            )
        }

        SharedTransitionLayout {
            AnimatedContent(
                targetState = isExpanded,
                transitionSpec = { fadeIn(tween(300)) togetherWith fadeOut(tween(200)) },
                label = "reportersSection",
            ) { expanded ->
                if (!expanded) {
                    WeekStripContent(
                        slots = weekSlots,
                        rowState = rowState,
                        onDayTapped = { index -> onIntent(ReaderContract.Intent.DaySlotTapped(index)) },
                        animatedVisibilityScope = this,
                    )
                } else {
                    MonthCarouselContent(
                        calendarDays = calendarDays,
                        weekSlots = weekSlots,
                        weekEpochDays = weekEpochDays,
                        animatedVisibilityScope = this,
                        onIntent = onIntent,
                    )
                }
            }
        }
    }
}

@Composable
private fun SharedTransitionScope.WeekStripContent(
    slots: List<ReaderContract.DaySlot>,
    rowState: LazyListState,
    onDayTapped: (Int) -> Unit,
    animatedVisibilityScope: AnimatedVisibilityScope,
) {
    val badgeModifier = with(animatedVisibilityScope) {
        Modifier
            .renderInSharedTransitionScopeOverlay(zIndexInOverlay = 1f)
            .animateEnterExit(
                enter = fadeIn(tween(200)),
                exit = fadeOut(tween(80)),
            )
    }
    LazyRow(
        state = rowState,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        items(slots.size) { index ->
            val slot = slots[index]
            val sharedState = rememberSharedContentState(key = "reporter_${slot.epochDay}")
            DaySlotItem(
                slot = slot,
                onClick = { onDayTapped(index) },
                portraitModifier = Modifier.sharedElement(
                    sharedContentState = sharedState,
                    animatedVisibilityScope = animatedVisibilityScope,
                ),
                badgeModifier = badgeModifier,
            )
        }
    }
}

@Composable
private fun CenterTodayEffect(rowState: LazyListState, todayIndex: Int, density: Density) {
    LaunchedEffect(todayIndex) {
        if (todayIndex < 0) return@LaunchedEffect
        snapshotFlow { rowState.layoutInfo.viewportSize.width }
            .filter { it > 0 }
            .first()
            .let { viewportWidth ->
                val itemWidthPx = with(density) { 72.dp.roundToPx() }
                val offset = -(viewportWidth / 2 - itemWidthPx / 2)
                rowState.scrollToItem(index = todayIndex, scrollOffset = offset)
            }
    }
}

@Composable
private fun SharedTransitionScope.MonthCarouselContent(
    calendarDays: List<ReaderContract.CalendarDay>,
    weekSlots: List<ReaderContract.DaySlot>,
    weekEpochDays: Set<Long>,
    animatedVisibilityScope: AnimatedVisibilityScope,
    onIntent: (ReaderContract.Intent) -> Unit,
) {
    val todayEpochDay = remember(weekSlots) { weekSlots.firstOrNull { it.isToday }?.epochDay ?: 0L }
    val todayDate = remember(todayEpochDay) { LocalDate.fromEpochDays(todayEpochDay.toInt()) }
    val startYear = remember(todayDate) { todayDate.year - CAROUSEL_START_YEAR_OFFSET }
    val lastYear = remember(todayDate) { todayDate.year + 1 }
    val totalMonths = remember(todayDate) { (lastYear - startYear + 1) * 12 }
    val initialPage = remember(todayDate) { (todayDate.year - startYear) * 12 + (todayDate.monthNumber - 1) }
    val pagerState = rememberPagerState(initialPage = initialPage) { totalMonths }
    val coroutineScope = rememberCoroutineScope()
    val currentYear = startYear + pagerState.currentPage / 12
    val loadedPage = remember(calendarDays) { epochDayToPage(calendarDays, startYear, initialPage) }
    val pageCache = remember { mutableStateMapOf<Int, List<ReaderContract.CalendarDay>>() }

    LaunchedEffect(calendarDays) {
        if (calendarDays.isNotEmpty()) pageCache[loadedPage] = calendarDays
    }

    LaunchedEffect(pagerState.settledPage) {
        onIntent(ReaderContract.Intent.MonthPageChanged(
            year = startYear + pagerState.settledPage / 12,
            month = pagerState.settledPage % 12 + 1,
        ))
    }

    Column {
        YearSelector(
            year = currentYear,
            prevEnabled = currentYear > startYear,
            nextEnabled = currentYear < lastYear,
            prevYearDescription = stringResource(Res.string.reader_calendar_prev_year),
            nextYearDescription = stringResource(Res.string.reader_calendar_next_year),
            onPrevYear = {
                val target = ((currentYear - 1 - startYear) * 12).coerceAtLeast(0)
                coroutineScope.launch { pagerState.animateScrollToPage(target) }
            },
            onNextYear = {
                val target = ((currentYear + 1 - startYear) * 12).coerceAtMost(totalMonths - 1)
                coroutineScope.launch { pagerState.animateScrollToPage(target) }
            },
        )
        HorizontalPager(state = pagerState) { page ->
            val daysForPage = pageCache[page] ?: buildSkeletonDays(page, startYear, todayEpochDay)
            CalendarCard(
                days = daysForPage,
                onDayTapped = { epochDay ->
                    val day = daysForPage.find { it.epochDay == epochDay }
                    if (day?.isFuture == true) {
                        onIntent(ReaderContract.Intent.SelectFutureDay(epochDay))
                    } else {
                        onIntent(ReaderContract.Intent.HistoryDayTapped(epochDay))
                    }
                },
                sharedElementModifierFor = sharedModifierForPage(page, initialPage, weekEpochDays, animatedVisibilityScope),
                modifier = Modifier.padding(bottom = 16.dp),
            )
        }
    }
}

@Composable
private fun SharedTransitionScope.sharedModifierForPage(
    page: Int,
    initialPage: Int,
    weekEpochDays: Set<Long>,
    animatedVisibilityScope: AnimatedVisibilityScope,
): @Composable (epochDay: Long) -> Modifier {
    return if (page == initialPage) {
        { epochDay ->
            if (epochDay in weekEpochDays) {
                Modifier.sharedElement(
                    sharedContentState = rememberSharedContentState(key = "reporter_$epochDay"),
                    animatedVisibilityScope = animatedVisibilityScope,
                )
            } else {
                Modifier
            }
        }
    } else {
        { Modifier }
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
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
        horizontalArrangement = Arrangement.Center,
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
}

private fun buildSkeletonDays(
    page: Int,
    startYear: Int,
    todayEpochDay: Long,
): List<ReaderContract.CalendarDay> {
    val monthStart = LocalDate(startYear + page / 12, page % 12 + 1, 1)
    val monthStartEpoch = monthStart.toEpochDays().toLong()
    val daysInMonth = monthStart.plus(1, DateTimeUnit.MONTH).toEpochDays() - monthStart.toEpochDays()
    return (0 until daysInMonth.toInt()).map { d ->
        val epochDay = monthStartEpoch + d
        ReaderContract.CalendarDay(
            epochDay = epochDay,
            dateNumber = LocalDate.fromEpochDays(epochDay.toInt()).dayOfMonth,
            isToday = epochDay == todayEpochDay,
            isFuture = epochDay > todayEpochDay,
            hasData = false,
            figurePortraitUrl = null,
            figureName = null,
            overrideFigureId = null,
        )
    }
}

private fun epochDayToPage(
    calendarDays: List<ReaderContract.CalendarDay>,
    startYear: Int,
    fallback: Int,
): Int {
    val epochDay = calendarDays.firstOrNull()?.epochDay ?: return fallback
    val date = LocalDate.fromEpochDays(epochDay.toInt())
    return (date.year - startYear) * 12 + (date.monthNumber - 1)
}
