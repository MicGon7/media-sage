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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mediasage.theme.BrandAmber
import kotlinx.datetime.LocalDate

// Fixed height for every day cell (date label + 28.dp portrait + padding) so week rows
// within a month card measure consistently.
private val MonthDayCellHeight = 50.dp

@Composable
fun CalendarCard(
    days: List<ReaderHistoryContract.CalendarDay>,
    onDayTapped: (epochDay: Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.padding(horizontal = 16.dp)) {
        Surface(
            shape = MaterialTheme.shapes.large,
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.surfaceVariant,
            tonalElevation = 0.dp,
            shadowElevation = 6.dp
        ) {
            Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp)) {
                MonthHeader(days)
                WeekDayHeaderRow()
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
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MonthHeader(days: List<ReaderHistoryContract.CalendarDay>) {
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
    day: ReaderHistoryContract.CalendarDay?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (day == null) {
        Box(modifier = modifier.height(MonthDayCellHeight))
        return
    }
    Column(
        modifier = modifier.height(MonthDayCellHeight).clickable(onClick = onClick).padding(vertical = 2.dp),
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
                DayPortrait(day)
            }
        }
    }
}

@Composable
private fun DayPortrait(
    day: ReaderHistoryContract.CalendarDay,
    showRing: Boolean = true,
) {
    if (day.figurePortraitUrl != null) {
        FigurePortraitImage(
            imageUrl = day.figurePortraitUrl,
            name = day.figureName,
            size = 28.dp,
            isToday = day.isToday,
            showRing = showRing,
        )
    } else {
        val fallbackModifier = Modifier.size(28.dp).clip(CircleShape)
            .then(if (day.isToday && showRing) Modifier.solidCircleBorder(BrandAmber, 2.dp) else Modifier)
        Box(
            modifier = fallbackModifier.background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Text("†", fontSize = 12.sp, color = MaterialTheme.colorScheme.onPrimaryContainer)
        }
    }
}

private fun buildMonthCells(
    days: List<ReaderHistoryContract.CalendarDay>,
    firstDayOffset: Int,
): List<ReaderHistoryContract.CalendarDay?> {
    val cells = mutableListOf<ReaderHistoryContract.CalendarDay?>()
    repeat(firstDayOffset) { cells.add(null) }
    cells.addAll(days)
    while (cells.size % 7 != 0) cells.add(null)
    return cells
}
