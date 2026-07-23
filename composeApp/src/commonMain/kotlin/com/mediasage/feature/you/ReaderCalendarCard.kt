package com.mediasage.feature.you

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mediasage.theme.BrandAmber
import kotlinx.datetime.LocalDate

// A month spans at most six week rows. Reserving a fixed six-row grid area keeps
// every month card the same height, so the carousel does not jump when scrolling
// between months with different week counts (e.g. a 5-row month next to a 6-row one).
private const val CALENDAR_WEEK_ROWS = 6

// Fixed height for every day cell (date label + 28.dp portrait + padding) so filled
// and empty rows measure identically and month heights stay constant.
private val MonthDayCellHeight = 50.dp

// The grid always reserves six rows so the card height never changes between months.
// Shorter months leave a small gap at the bottom — intentional space held for future
// Reader features.
private val CalendarBodyHeight = MonthDayCellHeight * CALENDAR_WEEK_ROWS

@Composable
fun CalendarCard(
    days: List<ReaderContract.CalendarDay>,
    onDayTapped: (epochDay: Long) -> Unit,
    sharedElementModifierFor: @Composable (epochDay: Long) -> Modifier = { Modifier },
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.padding(horizontal = 16.dp)) {
        Surface(
            shape = MaterialTheme.shapes.large,
            modifier = Modifier.fillMaxWidth(),
            tonalElevation = 0.dp,
            shadowElevation = 2.dp
        ) {
            Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp)) {
                MonthHeader(days)
                WeekDayHeaderRow()
                Column(modifier = Modifier.height(CalendarBodyHeight)) {
                    val firstDayOffset = days.firstOrNull()?.let {
                        LocalDate.fromEpochDays(it.epochDay.toInt()).dayOfWeek.ordinal
                    } ?: 0
                    val cells = buildMonthCells(days, firstDayOffset)
                    cells.chunked(7).forEach { week ->
                        Row(modifier = Modifier.fillMaxWidth()) {
                            week.forEach { day ->
                                MonthDayCell(
                                    day = day,
                                    onClick = { day?.let { onDayTapped(it.epochDay) } },
                                    sharedElementModifierFor = sharedElementModifierFor,
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MonthHeader(days: List<ReaderContract.CalendarDay>) {
    val title = remember(days) {
        days.firstOrNull()?.let { day ->
            val date = LocalDate.fromEpochDays(day.epochDay.toInt())
            "${date.month.name} ${date.year}"
        } ?: ""
    }
    Text(
        text = title,
        style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.5.sp),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(vertical = 4.dp),
    )
}

@Composable
private fun WeekDayHeaderRow() {
    Row(modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp)) {
        listOf("M", "T", "W", "T", "F", "S", "S").forEach { label ->
            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                )
            }
        }
    }
}

@Composable
private fun MonthDayCell(
    day: ReaderContract.CalendarDay?,
    onClick: () -> Unit,
    sharedElementModifierFor: @Composable (epochDay: Long) -> Modifier = { Modifier },
    modifier: Modifier = Modifier,
) {
    if (day == null) {
        Box(modifier = modifier.height(MonthDayCellHeight))
        return
    }
    val portraitMod = sharedElementModifierFor(day.epochDay)
    Column(
        modifier = modifier
            .height(MonthDayCellHeight)
            .then(if (day.isFuture) Modifier else Modifier.clickable(onClick = onClick))
            .padding(vertical = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterVertically),
    ) {
        Text(
            text = "${day.dateNumber}",
            style = MaterialTheme.typography.labelSmall,
            color = when {
                day.isToday -> BrandAmber
                day.hasData -> MaterialTheme.colorScheme.onSurface
                else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f)
            },
            fontWeight = if (day.isToday) FontWeight.Bold else FontWeight.Normal,
        )
        Box(modifier = Modifier.size(28.dp), contentAlignment = Alignment.Center) {
            if (day.isToday && !day.hasData) {
                Box(Modifier.size(6.dp).clip(CircleShape).background(BrandAmber))
            }
            if (day.hasData) {
                DayPortrait(day, portraitMod)
            }
        }
    }
}

@Composable
private fun DayPortrait(
    day: ReaderContract.CalendarDay,
    sharedModifier: Modifier = Modifier,
    showRing: Boolean = true,
) {
    val isPast = !day.isToday && !day.isFuture
    if (day.figurePortraitUrl != null) {
        FigurePortraitImage(
            imageUrl = day.figurePortraitUrl,
            name = day.figureName,
            size = 28.dp,
            isToday = day.isToday,
            isPast = isPast,
            showRing = showRing,
            modifier = sharedModifier,
        )
    } else {
        val fallbackModifier = sharedModifier.size(28.dp).clip(CircleShape)
            .then(if (day.isToday && showRing) Modifier.solidCircleBorder(BrandAmber, 2.dp) else Modifier)
            .then(if (isPast) Modifier.alpha(0.6f) else Modifier)
        Box(
            modifier = fallbackModifier.background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Text("†", fontSize = 12.sp, color = MaterialTheme.colorScheme.onPrimaryContainer)
        }
    }
}

private fun buildMonthCells(
    days: List<ReaderContract.CalendarDay>,
    firstDayOffset: Int,
): List<ReaderContract.CalendarDay?> {
    val cells = mutableListOf<ReaderContract.CalendarDay?>()
    repeat(firstDayOffset) { cells.add(null) }
    cells.addAll(days)
    while (cells.size % 7 != 0) cells.add(null)
    return cells
}
