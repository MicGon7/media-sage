package com.mediasage.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.mediasage.theme.ComicBrown
import com.mediasage.theme.ComicCream
import com.mediasage.theme.ComicInk
import com.mediasage.theme.ComicTan
import com.mediasage.theme.MediaSageTheme

/**
 * A compact chip styled after the app's comic / vintage-newspaper illustration — the same
 * sepia gradient, ink text, and brown border as [MediaSageEntryCard], sized to sit as a small,
 * secondary affordance attached to a block of content (e.g. beneath a briefing's reflection
 * text) rather than a standalone call to action. In light mode the background and text come
 * from the fixed comic palette; in dark mode the background becomes a neutral elevated surface
 * and the border/text switch to [MaterialTheme.colorScheme] equivalents, matching the same
 * dark-mode treatment as [MediaSageEntryCard] — a fixed warm tone here would clash with that
 * neutral surface rather than the comic palette itself.
 */
@Composable
fun MediaSageComicChip(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isDark = MediaSageTheme.isDark
    val borderColor = if (isDark) MaterialTheme.colorScheme.outline else ComicBrown
    val contentColor = if (isDark) MaterialTheme.colorScheme.onSurface else ComicInk
    val backgroundModifier = if (isDark) {
        Modifier.background(MaterialTheme.colorScheme.surfaceColorAtElevation(4.dp))
    } else {
        Modifier.background(Brush.horizontalGradient(colors = listOf(ComicCream, ComicTan)))
    }
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(50),
        border = BorderStroke(1.dp, borderColor),
        color = Color.Transparent,
    ) {
        Row(
            modifier = backgroundModifier.padding(horizontal = 12.dp, vertical = 6.dp),
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
