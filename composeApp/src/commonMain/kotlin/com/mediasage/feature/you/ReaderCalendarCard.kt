package com.mediasage.feature.you

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.mediasage.theme.BrandAmber
import kotlinx.datetime.LocalDate
import mediasage.composeapp.generated.resources.Res
import mediasage.composeapp.generated.resources.you_calendar_no_data
import mediasage.composeapp.generated.resources.you_calendar_section_title
import org.jetbrains.compose.resources.stringResource

@Composable
fun CalendarCard(
    days: List<ReaderContract.CalendarDay>,
    onDayTapped: (epochDay: Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Text(
            text = stringResource(Res.string.you_calendar_section_title),
            style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.5.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        )
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(days, key = { it.epochDay }) { day ->
                RecentBriefingCell(day = day, onClick = { onDayTapped(day.epochDay) })
            }
        }
    }
}

@Composable
private fun RecentBriefingCell(day: ReaderContract.CalendarDay, onClick: () -> Unit) {
    val dayLabel = LocalDate.fromEpochDays(day.epochDay.toInt()).dayOfWeek.name.take(3)
    val primary = MaterialTheme.colorScheme.primary
    val surfaceVariant = MaterialTheme.colorScheme.surfaceVariant
    Column(
        modifier = Modifier.clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = dayLabel,
            style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.sp),
            color = if (day.isToday) primary else MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = if (day.isToday) FontWeight.Bold else FontWeight.Normal,
        )
        Box(contentAlignment = Alignment.Center) {
            if (day.hasData && day.figurePortraitUrl != null) {
                AsyncImage(
                    model = day.figurePortraitUrl,
                    contentDescription = day.figureName,
                    modifier = Modifier.cellCircle(day.isToday),
                    contentScale = ContentScale.Crop,
                    alignment = Alignment.TopCenter,
                )
            } else if (day.hasData) {
                Box(
                    modifier = cellCircle(day.isToday).background(surfaceVariant),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(text = "†", fontSize = 18.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                Box(
                    modifier = cellCircle(false)
                        .background(surfaceVariant.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = stringResource(Res.string.you_calendar_no_data),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}

private fun Modifier.cellCircle(isToday: Boolean): Modifier =
    this.size(48.dp)
        .clip(CircleShape)
        .then(if (isToday) Modifier.border(2.dp, BrandAmber, CircleShape) else Modifier)
