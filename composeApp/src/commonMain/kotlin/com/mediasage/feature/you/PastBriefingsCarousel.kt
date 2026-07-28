package com.mediasage.feature.you

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import mediasage.composeapp.generated.resources.Res
import mediasage.composeapp.generated.resources.you_recent_briefing_yesterday
import mediasage.composeapp.generated.resources.you_recent_briefings_more
import mediasage.composeapp.generated.resources.you_recent_briefings_section_title
import org.jetbrains.compose.resources.stringResource

@Composable
fun PastBriefingsCarousel(
    cards: List<ReaderContract.PastBriefingCard>,
    onSeeMore: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        SectionLabel(
            text = stringResource(Res.string.you_recent_briefings_section_title),
            modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 12.dp),
        )
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(cards, key = { it.epochDay }) { card ->
                PastBriefingCard(card = card)
            }
            item {
                SeeMoreCard(onClick = onSeeMore, modifier = Modifier.fillParentMaxHeight())
            }
        }
    }
}

@Composable
private fun PastBriefingCard(card: ReaderContract.PastBriefingCard, modifier: Modifier = Modifier) {
    ElevatedCard(
        modifier = modifier.width(240.dp),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 4.dp),
    ) {
        Column {
            // Top accent bar, not a side ribbon — visually distinct from the Memory Quote card at a glance.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .background(
                        color = MaterialTheme.colorScheme.tertiary,
                        shape = RoundedCornerShape(
                            topStart = MaterialTheme.shapes.large.topStart,
                            topEnd = MaterialTheme.shapes.large.topEnd,
                            bottomStart = CornerSize(0.dp),
                            bottomEnd = CornerSize(0.dp),
                        ),
                    ),
            )
            Row(
                modifier = Modifier.padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                PastBriefingPortrait(card)
                Column {
                    Text(
                        text = card.dayLabel.resolve(),
                        style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.sp),
                        color = MaterialTheme.colorScheme.tertiary,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = card.figureName,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                    Text(
                        text = card.inspiration,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 4,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun PastBriefingPortrait(card: ReaderContract.PastBriefingCard) {
    if (card.figureImageUrl != null) {
        AsyncImage(
            model = card.figureImageUrl,
            contentDescription = card.figureName,
            modifier = Modifier.size(56.dp).clip(RoundedCornerShape(12.dp)),
            contentScale = ContentScale.Crop,
            alignment = Alignment.TopCenter,
            error = rememberVectorPainter(Icons.Filled.Person),
            fallback = rememberVectorPainter(Icons.Filled.Person),
        )
    } else {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Text("†", fontSize = 20.sp, color = MaterialTheme.colorScheme.onPrimaryContainer)
        }
    }
}

@Composable
private fun ReaderContract.DayLabel.resolve(): String = when (this) {
    ReaderContract.DayLabel.Yesterday -> stringResource(Res.string.you_recent_briefing_yesterday)
    is ReaderContract.DayLabel.Text -> value
}

@Composable
private fun SeeMoreCard(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        onClick = onClick,
        modifier = modifier.width(96.dp),
        shape = MaterialTheme.shapes.large,
        tonalElevation = 0.dp,
        shadowElevation = 2.dp,
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = stringResource(Res.string.you_recent_briefings_more),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}
