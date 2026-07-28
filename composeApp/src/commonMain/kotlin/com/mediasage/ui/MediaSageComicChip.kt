package com.mediasage.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.mediasage.theme.ComicBrown
import com.mediasage.theme.ComicCaramel
import com.mediasage.theme.ComicCream
import com.mediasage.theme.ComicInk
import com.mediasage.theme.ComicTan
import com.mediasage.theme.MediaSageTheme

/**
 * A compact chip styled after the app's comic / vintage-newspaper illustration — the same
 * sepia gradient, ink text, and brown border as [MediaSageEntryCard], sized to sit as a small,
 * secondary affordance attached to a block of content (e.g. beneath a briefing's reflection
 * text) rather than a standalone call to action. Colors come from the fixed comic palette
 * rather than [MaterialTheme.colorScheme] — but unlike [ThemeChip], the palette itself flips to
 * a darker sepia treatment in dark mode so it doesn't read as a bright disconnect against the
 * rest of a dark screen.
 */
@Composable
fun MediaSageComicChip(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isDark = isSystemInDarkTheme()
    val gradientColors = if (isDark) listOf(ComicBrown, ComicInk) else listOf(ComicCream, ComicTan)
    val borderColor = if (isDark) ComicCaramel else ComicBrown
    val contentColor = if (isDark) ComicTan else ComicInk
    Surface(
        modifier = modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(50),
        border = BorderStroke(1.dp, borderColor),
        color = Color.Transparent,
    ) {
        Row(
            modifier = Modifier
                .background(Brush.horizontalGradient(colors = gradientColors))
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = contentColor, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text(text = label, style = MaterialTheme.typography.labelMedium, color = contentColor)
        }
    }
}

// region Previews

@Preview(showBackground = true)
@Composable
private fun MediaSageComicChipPreview() {
    MediaSageTheme {
        MediaSageComicChip(icon = Icons.Outlined.Share, label = "Share", onClick = {})
    }
}

// endregion
