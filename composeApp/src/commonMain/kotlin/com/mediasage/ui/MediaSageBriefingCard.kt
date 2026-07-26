package com.mediasage.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.mediasage.theme.LensFaith
import com.mediasage.theme.LensGrace
import com.mediasage.theme.LensGrief
import com.mediasage.theme.LensHope
import com.mediasage.theme.LensJustice
import com.mediasage.theme.LensLove
import com.mediasage.theme.LensPerseverance
import com.mediasage.theme.LensRepentance
import mediasage.composeapp.generated.resources.Res
import mediasage.composeapp.generated.resources.briefing_card_based_on
import org.jetbrains.compose.resources.stringResource

private val CardImageHeight = 220.dp
private val FigurePlaceholderSize = 80.dp

/**
 * The shared "briefing look": figure portrait and name, with an optional theme chip. This is the
 * exact visual structure the live Briefing screen and the Day Detail screen both render for a
 * briefing's figure — split out from [MediaSageBriefingBody] so a screen that switches between
 * several of a figure's briefings (e.g. morning/evening) can show this once and only swap the body
 * underneath. Theme and sources live per-briefing, not here — see [MediaSageBriefingBody].
 */
@Composable
fun MediaSageBriefingHeader(
    figureName: String?,
    figureImageUrl: String?,
    modifier: Modifier = Modifier,
    onFigureTap: (() -> Unit)? = null,
    theme: String? = null,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(CardImageHeight)
                .clip(MaterialTheme.shapes.small)
                .then(if (onFigureTap != null) Modifier.clickable(onClick = onFigureTap) else Modifier),
        ) {
            if (figureImageUrl != null) {
                AsyncImage(
                    model = figureImageUrl,
                    contentDescription = figureName,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                    colorFilter = SepiaColorFilter,
                )
            } else {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surfaceVariant) {
                    Box(contentAlignment = Alignment.Center) {
                        FigurePlaceholder(name = figureName.orEmpty(), size = FigurePlaceholderSize)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        if (figureName != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = figureName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                if (theme != null) {
                    Spacer(modifier = Modifier.width(8.dp))
                    ThemeChip(theme = theme)
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
        }
    }
}

/**
 * The scripture and reflection body of a briefing — the part that actually changes between a
 * figure's morning and evening briefing on the same day, including its own source citation. See
 * [MediaSageBriefingHeader] for the portrait/name section this follows.
 */
@Composable
fun MediaSageBriefingBody(
    scriptureReference: String,
    scriptureText: String,
    insight: String,
    implication: String,
    inspiration: String,
    modifier: Modifier = Modifier,
    sources: List<String> = emptyList(),
    trailingContent: @Composable () -> Unit = {},
) {
    Column(modifier = modifier.fillMaxWidth()) {
        if (sources.isNotEmpty()) {
            Text(
                text = "${stringResource(Res.string.briefing_card_based_on)} ${sources.joinToString(", ")}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontStyle = FontStyle.Italic,
            )
            Spacer(modifier = Modifier.height(6.dp))
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.primary, thickness = 1.dp)
        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = scriptureReference,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "“$scriptureText”",
            style = MaterialTheme.typography.bodyMedium,
            fontStyle = FontStyle.Italic,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(modifier = Modifier.height(8.dp))

        Text(text = insight, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(modifier = Modifier.height(8.dp))
        Text(text = implication, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(modifier = Modifier.height(8.dp))
        Text(text = inspiration, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)

        Spacer(modifier = Modifier.height(12.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, thickness = 0.5.dp)
        trailingContent()
    }
}

/**
 * The full briefing card — [MediaSageBriefingHeader] followed by [MediaSageBriefingBody] — for the
 * common case of showing one briefing at a time (the live Briefing screen, or a Day Detail day with
 * only one briefing generated).
 */
@Composable
fun MediaSageBriefingCard(
    figureName: String?,
    figureImageUrl: String?,
    scriptureReference: String,
    scriptureText: String,
    insight: String,
    implication: String,
    inspiration: String,
    modifier: Modifier = Modifier,
    onFigureTap: (() -> Unit)? = null,
    theme: String? = null,
    sources: List<String> = emptyList(),
    trailingContent: @Composable () -> Unit = {},
) {
    Column(modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
        MediaSageBriefingHeader(
            figureName = figureName,
            figureImageUrl = figureImageUrl,
            onFigureTap = onFigureTap,
            theme = theme,
        )
        MediaSageBriefingBody(
            scriptureReference = scriptureReference,
            scriptureText = scriptureText,
            insight = insight,
            implication = implication,
            inspiration = inspiration,
            sources = sources,
            trailingContent = trailingContent,
        )
    }
}

@Composable
fun ThemeChip(theme: String) {
    val color = when (theme.uppercase()) {
        "LOVE" -> LensLove
        "GRACE" -> LensGrace
        "FAITH" -> LensFaith
        "GRIEF" -> LensGrief
        "REPENTANCE" -> LensRepentance
        "HOPE" -> LensHope
        "JUSTICE" -> LensJustice
        "PERSEVERANCE" -> LensPerseverance
        else -> MaterialTheme.colorScheme.primary
    }
    Text(
        text = theme.lowercase().replaceFirstChar { it.uppercase() },
        style = MaterialTheme.typography.labelSmall,
        color = color,
        modifier = Modifier
            .border(width = 1.dp, color = color, shape = RoundedCornerShape(50))
            .padding(horizontal = 8.dp, vertical = 2.dp),
    )
}
