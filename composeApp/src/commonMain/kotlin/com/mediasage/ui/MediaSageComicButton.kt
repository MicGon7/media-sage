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
import com.mediasage.theme.ComicCaramel
import com.mediasage.theme.ComicInk
import com.mediasage.theme.ComicTan
import com.mediasage.theme.MediaSageTheme

/**
 * A button styled after the app's comic / vintage-newspaper illustration — an ink-brown fill,
 * caramel border, and parchment text/icon, the same trio of tones as the coat sleeve, hand, and
 * cuff in the source artwork. Colors come from the fixed comic palette rather than
 * [MaterialTheme.colorScheme], so the look stays consistent across the active
 * [com.mediasage.theme.AppTheme] variant and dark mode, the same precedent as [ThemeChip]. The
 * dark fill paired with light text keeps contrast strong regardless of how rich the palette gets.
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
        color = ComicInk,
        border = BorderStroke(1.5.dp, ComicCaramel),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = ComicTan)
            Spacer(modifier = Modifier.width(8.dp))
            Text(text = label, style = MaterialTheme.typography.labelLarge, color = ComicTan)
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
