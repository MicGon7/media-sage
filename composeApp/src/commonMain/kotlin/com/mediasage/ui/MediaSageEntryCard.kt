package com.mediasage.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.mediasage.theme.ComicBrown
import com.mediasage.theme.ComicCream
import com.mediasage.theme.ComicInk
import com.mediasage.theme.ComicTan
import com.mediasage.theme.MediaSageTheme

/**
 * A navigation-entry row styled after the app's comic / vintage-newspaper illustration (sepia
 * gradient, ink text) — a title, a subtitle, and a tap target that navigates elsewhere. Colors
 * come from the fixed comic palette rather than [MaterialTheme.colorScheme] — but unlike
 * [ThemeChip], the palette itself flips to a darker sepia treatment in dark mode so it doesn't
 * read as a bright disconnect against the rest of a dark screen, the same precedent as
 * [MediaSageComicChip].
 */
@Composable
fun MediaSageEntryCard(
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isDark = MediaSageTheme.isDark
    val gradientColors = if (isDark) listOf(ComicBrown, ComicInk) else listOf(ComicCream, ComicTan)
    val contentColor = if (isDark) ComicTan else ComicInk
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.large,
        color = Color.Transparent,
        tonalElevation = 0.dp,
        shadowElevation = 2.dp,
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .background(Brush.verticalGradient(colors = gradientColors))
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = contentColor,
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = contentColor,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = null,
                tint = contentColor,
            )
        }
    }
}

// region Previews

@Preview(showBackground = true)
@Composable
private fun MediaSageEntryCardPreview() {
    MediaSageTheme {
        MediaSageEntryCard(
            title = "Saved",
            subtitle = "Your bookmarked encouragements",
            onClick = {},
        )
    }
}

// endregion
