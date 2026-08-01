package com.mediasage.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mediasage.theme.ComicCream
import com.mediasage.theme.ComicInk
import com.mediasage.theme.ComicTan
import com.mediasage.theme.MediaSageTheme
import mediasage.composeapp.generated.resources.Res
import mediasage.composeapp.generated.resources.figure_detail_memorize_quote
import mediasage.composeapp.generated.resources.figure_detail_memorized_quote
import org.jetbrains.compose.resources.stringResource

private val PinBadgeSize = 36.dp

/**
 * The quote-and-pin card shared by [com.mediasage.feature.figures.FigureDetailScreen]'s Quotes tab
 * and [com.mediasage.feature.quotes.QuotesScreen]'s memorized-quote list: quote text on the card
 * surface, a gradient transition into a fixed sepia footer carrying an optional attribution/context
 * line, and a pin badge straddling the card's top-right corner — half resting on the card, half
 * floating off it.
 */
@Composable
fun QuoteCard(
    quoteText: String,
    isPinned: Boolean,
    onPinQuote: () -> Unit,
    footerText: String? = null,
    modifier: Modifier = Modifier,
) {
    val isDark = MediaSageTheme.isDark
    val cardSurface = if (isDark) {
        MaterialTheme.colorScheme.surfaceColorAtElevation(4.dp)
    } else {
        MaterialTheme.colorScheme.surface
    }

    Box(modifier = modifier.fillMaxWidth()) {
        ElevatedCard(
            onClick = onPinQuote,
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.large,
            colors = CardDefaults.elevatedCardColors(containerColor = cardSurface),
            elevation = CardDefaults.elevatedCardElevation(defaultElevation = 4.dp),
        ) {
            Column {
                Text(
                    text = "“$quoteText”",
                    style = MaterialTheme.typography.bodyLarge,
                    fontStyle = FontStyle.Italic,
                    color = MaterialTheme.colorScheme.onSurface,
                    lineHeight = 24.sp,
                    modifier = Modifier.padding(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 8.dp),
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(16.dp)
                        .background(Brush.verticalGradient(colors = listOf(cardSurface, ComicCream)))
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Brush.verticalGradient(colors = listOf(ComicCream, ComicTan)))
                        .padding(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (!footerText.isNullOrBlank()) {
                        Text(
                            text = footerText,
                            style = MaterialTheme.typography.labelSmall,
                            color = ComicInk,
                        )
                    }
                }
            }
        }

        PinBadge(
            isPinned = isPinned,
            onClick = onPinQuote,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset(x = PinBadgeSize / 2, y = -PinBadgeSize / 2),
        )
    }
}

private val PinBadgeBounceSpring = spring<Float>(
    dampingRatio = Spring.DampingRatioMediumBouncy,
    stiffness = Spring.StiffnessMedium,
)

@Composable
private fun PinBadge(isPinned: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val scale = remember { Animatable(1f) }
    var isFirstComposition by remember { mutableStateOf(true) }
    LaunchedEffect(isPinned) {
        if (isFirstComposition) {
            isFirstComposition = false
            return@LaunchedEffect
        }
        scale.snapTo(0.7f)
        scale.animateTo(1f, animationSpec = PinBadgeBounceSpring)
    }

    Surface(
        onClick = onClick,
        modifier = modifier
            .size(PinBadgeSize)
            .graphicsLayer {
                scaleX = scale.value
                scaleY = scale.value
            },
        shape = CircleShape,
        color = if (isPinned) ComicTan else MaterialTheme.colorScheme.surface,
        shadowElevation = 3.dp,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = if (isPinned) Icons.Filled.PushPin else Icons.Outlined.PushPin,
                contentDescription = stringResource(
                    if (isPinned) Res.string.figure_detail_memorized_quote
                    else Res.string.figure_detail_memorize_quote
                ),
                tint = ComicInk,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

// region Previews

@Preview(showBackground = true)
@Composable
private fun QuoteCardPreview() {
    MediaSageTheme {
        QuoteCard(
            quoteText = "The soul is refreshed by the words of the wise.",
            isPinned = true,
            onPinQuote = {},
            footerText = "In response to: A story of quiet perseverance",
            modifier = Modifier.padding(16.dp),
        )
    }
}

// endregion
