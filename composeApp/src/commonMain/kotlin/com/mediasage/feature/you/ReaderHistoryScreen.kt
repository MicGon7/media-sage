package com.mediasage.feature.you

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.outlined.Today
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mediasage.LocalIsDebugBuild
import com.mediasage.ui.MediaSageBackRow
import kotlinx.coroutines.launch
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus
import mediasage.composeapp.generated.resources.Res
import mediasage.composeapp.generated.resources.reader_calendar_next_year
import mediasage.composeapp.generated.resources.reader_calendar_prev_year
import mediasage.composeapp.generated.resources.reader_calendar_today
import mediasage.composeapp.generated.resources.you_calendar_section_title
import mediasage.composeapp.generated.resources.you_nav_history
import org.jetbrains.compose.resources.stringResource

// Earliest year the carousel scrolls back to. Debug/pre-release builds expose seed
// data from 2025; release builds only carry production data from 2026 onward.
private const val PRE_RELEASE_START_YEAR = 2025
private const val RELEASE_START_YEAR = 2026

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderHistoryScreen(
    state: ReaderHistoryContract.UiState,
    onIntent: (ReaderHistoryContract.Intent) -> Unit,
    onNavigateBack: () -> Unit = {},
) {
    val ready = state as? ReaderHistoryContract.UiState.Ready
    val activeDetail = ready?.activeDetail
    if (activeDetail != null) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { onIntent(ReaderHistoryContract.Intent.DetailDismissed) },
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surface,
        ) {
            DayDetailSheetContent(dayDetail = activeDetail)
        }
    }

    Surface(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            MediaSageBackRow(onNavigateBack = onNavigateBack) {
                Text(
                    text = stringResource(Res.string.you_nav_history),
                    style = MaterialTheme.typography.titleLarge,
                )
            }
            if (ready != null) {
                Text(
                    text = stringResource(Res.string.you_calendar_section_title),
                    style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.5.sp),
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 8.dp),
                )
                HistoryCalendarCarousel(
                    todayEpochDay = ready.todayEpochDay,
                    calendarDays = ready.calendarDays,
                    onIntent = onIntent,
                )
            }
        }
    }
}

@Composable
private fun HistoryCalendarCarousel(
    todayEpochDay: Long,
    calendarDays: List<ReaderHistoryContract.CalendarDay>,
    onIntent: (ReaderHistoryContract.Intent) -> Unit,
) {
    val todayDate = remember(todayEpochDay) { LocalDate.fromEpochDays(todayEpochDay.toInt()) }
    val isDebugBuild = LocalIsDebugBuild.current
    val startYear = remember(todayDate, isDebugBuild) {
        val earliest = if (isDebugBuild) PRE_RELEASE_START_YEAR else RELEASE_START_YEAR
        minOf(earliest, todayDate.year)
    }
    val lastYear = remember(todayDate) { todayDate.year }
    val totalMonths = remember(todayDate) { (lastYear - startYear + 1) * 12 }
    val initialPage = remember(todayDate) { (todayDate.year - startYear) * 12 + (todayDate.monthNumber - 1) }
    val pagerState = rememberPagerState(initialPage = initialPage) { totalMonths }
    val coroutineScope = rememberCoroutineScope()
    val currentYear = startYear + pagerState.currentPage / 12
    val loadedPage = remember(calendarDays) { epochDayToPage(calendarDays, startYear, initialPage) }
    val pageCache = remember { mutableStateMapOf<Int, List<ReaderHistoryContract.CalendarDay>>() }

    LaunchedEffect(calendarDays) {
        if (calendarDays.isNotEmpty()) pageCache[loadedPage] = calendarDays
    }

    LaunchedEffect(pagerState.settledPage) {
        onIntent(
            ReaderHistoryContract.Intent.MonthPageChanged(
                year = startYear + pagerState.settledPage / 12,
                month = pagerState.settledPage % 12 + 1,
            ),
        )
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
            onToday = { coroutineScope.launch { pagerState.animateScrollToPage(initialPage) } },
        )
        HorizontalPager(state = pagerState) { page ->
            val daysForPage = pageCache[page] ?: buildSkeletonDays(page, startYear, todayEpochDay)
            CalendarCard(
                days = daysForPage,
                onDayTapped = { epochDay ->
                    val day = daysForPage.find { it.epochDay == epochDay }
                    if (day?.isFuture != true) {
                        onIntent(ReaderHistoryContract.Intent.DayTapped(epochDay))
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

private fun buildSkeletonDays(
    page: Int,
    startYear: Int,
    todayEpochDay: Long,
): List<ReaderHistoryContract.CalendarDay> {
    val monthStart = LocalDate(startYear + page / 12, page % 12 + 1, 1)
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
    startYear: Int,
    fallback: Int,
): Int {
    val epochDay = calendarDays.firstOrNull()?.epochDay ?: return fallback
    val date = LocalDate.fromEpochDays(epochDay.toInt())
    return (date.year - startYear) * 12 + (date.monthNumber - 1)
}
