package com.mediasage.feature.you

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import mediasage.composeapp.generated.resources.Res
import mediasage.composeapp.generated.resources.you_calendar_section_title
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mediasage.theme.BrandAmber
import kotlinx.datetime.LocalDate

@Composable
fun CalendarCard(
    days: List<ReaderContract.CalendarDay>,
    onDayTapped: (epochDay: Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.padding(horizontal = 16.dp)) {
        Text(
            text = stringResource(Res.string.you_calendar_section_title),
            style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.5.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(vertical = 8.dp),
        )
        Surface(
            shape = MaterialTheme.shapes.large,
            modifier = Modifier.fillMaxWidth(),
            tonalElevation = 0.dp,
            shadowElevation = 2.dp
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
    modifier: Modifier = Modifier,
) {
    if (day == null) {
        Box(modifier = modifier.size(44.dp))
        return
    }
    Column(
        modifier = modifier.clickable(onClick = onClick).padding(vertical = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
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
        when {
            day.hasData -> DayPortrait(day)
            day.isToday -> Box(Modifier.size(6.dp).clip(CircleShape).background(BrandAmber))
            else -> Spacer(Modifier.size(28.dp))
        }
    }
}

@Composable
private fun DayPortrait(day: ReaderContract.CalendarDay) {
    val isPast = !day.isToday && !day.isFuture
    if (day.figurePortraitUrl != null) {
        FigurePortraitImage(
            imageUrl = day.figurePortraitUrl,
            name = day.figureName,
            size = 28.dp,
            isToday = day.isToday,
            isPast = isPast,
        )
    } else {
        val fallbackModifier = Modifier.size(28.dp).clip(CircleShape)
            .then(if (day.isToday) Modifier.solidCircleBorder(BrandAmber, 2.dp) else Modifier)
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
