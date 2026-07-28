package com.mediasage.feature.you

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.mediasage.theme.ComicBrown
import com.mediasage.theme.ComicCream
import com.mediasage.theme.ComicInk
import com.mediasage.theme.ComicTan
import mediasage.composeapp.generated.resources.Res
import mediasage.composeapp.generated.resources.you_recent_briefing_yesterday
import mediasage.composeapp.generated.resources.you_recent_briefings_more
import mediasage.composeapp.generated.resources.you_recent_briefings_section_title
import org.jetbrains.compose.resources.stringResource

private val CardWidth = 260.dp
private val CardHeight = 132.dp
private val PortraitWidth = 100.dp

@Composable
fun PastBriefingsCarousel(
    cards: List<ReaderContract.PastBriefingCard>,
    onCardClick: (epochDay: Long) -> Unit,
    showSeeMore: Boolean,
    onSeeMore: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        SectionLabel(
            text = stringResource(Res.string.you_recent_briefings_section_title),
            modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 12.dp),
        )
        LazyRow(
            modifier = Modifier.height(CardHeight),
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(cards, key = { it.epochDay }) { card ->
                PastBriefingCard(card = card, onClick = { onCardClick(card.epochDay) })
            }
            if (showSeeMore) {
                item {
                    SeeMoreLink(onClick = onSeeMore, modifier = Modifier.fillParentMaxHeight())
                }
            }
        }
    }
}

@Composable
private fun PastBriefingCard(
    card: ReaderContract.PastBriefingCard,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ElevatedCard(
        onClick = onClick,
        modifier = modifier.width(CardWidth).height(CardHeight),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.elevatedCardColors(containerColor = Color.Transparent),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 4.dp),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Brush.verticalGradient(colors = listOf(ComicCream, ComicTan))),
            ) {
                PastBriefingPortrait(card, modifier = Modifier.width(PortraitWidth).fillMaxHeight())
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = card.dayLabel.resolve(),
                        style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.sp),
                        color = ComicBrown,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = card.inspiration,
                        style = MaterialTheme.typography.bodySmall,
                        color = ComicInk,
                        maxLines = 5,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun PastBriefingPortrait(
    card: ReaderContract.PastBriefingCard,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier) {
        if (card.figureImageUrl != null) {
            AsyncImage(
                model = card.figureImageUrl,
                contentDescription = card.figureName,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                alignment = Alignment.TopCenter,
                error = rememberVectorPainter(Icons.Filled.Person),
                fallback = rememberVectorPainter(Icons.Filled.Person),
            )
        } else {
            Box(
                modifier = Modifier.fillMaxSize()
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Text("†", fontSize = 20.sp, color = MaterialTheme.colorScheme.onPrimaryContainer)
            }
        }
        PortraitNameplate(
            name = card.figureName,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}

/** Last name captioned into the portrait's bottom edge, like a museum placard — frees the text
 * column for the inspiration line instead of repeating the full name there. */
@Composable
private fun PortraitNameplate(name: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.65f)),
                ),
            )
            .padding(horizontal = 6.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = name.substringAfterLast(' '),
            style = MaterialTheme.typography.labelSmall,
            color = Color.White,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun ReaderContract.DayLabel.resolve(): String = when (this) {
    ReaderContract.DayLabel.Yesterday -> stringResource(Res.string.you_recent_briefing_yesterday)
    is ReaderContract.DayLabel.Text -> value
}

@Composable
private fun SeeMoreLink(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.width(72.dp).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
            )
            Text(
                text = stringResource(Res.string.you_recent_briefings_more),
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}
