package com.mediasage.ui

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.mediasage.theme.ComicGradientOrientation
import com.mediasage.theme.MediaSageTheme

/**
 * A pill styled after the app's comic / vintage-newspaper illustration for a mobile action row
 * (e.g. Reflect/Study/Share beneath a briefing's reflection text) — a vertical sepia gradient,
 * ink text, and brown border via [MediaSageSurface], distinguishing it from the plain white card
 * it sits on.
 */
@Composable
fun MediaSageComicChip(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    MediaSageSurface(
        onClick = onClick,
        modifier = modifier,
        shape = MaterialTheme.shapes.medium,
        orientation = ComicGradientOrientation.Vertical,
        bordered = true,
    ) { contentColor ->
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = contentColor, modifier = Modifier.size(24.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(text = label, style = MaterialTheme.typography.labelLarge, color = contentColor)
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
