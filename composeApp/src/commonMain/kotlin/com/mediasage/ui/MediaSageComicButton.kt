package com.mediasage.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.mediasage.theme.ComicBrown
import com.mediasage.theme.ComicCream
import com.mediasage.theme.ComicInk
import com.mediasage.theme.MediaSageTheme

/**
 * A button styled after the app's comic / vintage-newspaper illustration (sepia surface, ink
 * text and border) — for use anywhere that look is wanted. Colors come from the fixed comic
 * palette rather than [MaterialTheme.colorScheme], so the look stays consistent across the
 * active [com.mediasage.theme.AppTheme] variant and dark mode, the same precedent as [ThemeChip].
 */
@Composable
fun MediaSageComicButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(50),
        color = ComicCream,
        border = BorderStroke(1.5.dp, ComicBrown),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = ComicInk)
            Spacer(modifier = Modifier.width(8.dp))
            Text(text = label, style = MaterialTheme.typography.labelLarge, color = ComicInk)
        }
    }
}

// region Previews

@Preview(showBackground = true)
@Composable
private fun MediaSageComicButtonPreview() {
    MediaSageTheme {
        MediaSageComicButton(icon = Icons.Outlined.Share, label = "Share", onClick = {})
    }
}

// endregion
