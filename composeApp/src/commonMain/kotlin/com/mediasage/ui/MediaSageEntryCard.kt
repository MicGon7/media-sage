package com.mediasage.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.mediasage.theme.MediaSageTheme

/**
 * A navigation-entry row styled after the app's comic / vintage-newspaper illustration (sepia
 * gradient, ink text) — a title, a subtitle, and a tap target that navigates elsewhere. Rendered
 * via [MediaSageSurface], shared with [MediaSageComicChip] and `PastBriefingCard`.
 */
@Composable
fun MediaSageEntryCard(
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    MediaSageSurface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shadowElevation = 2.dp,
    ) { contentColor ->
        Row(
            modifier = Modifier.padding(16.dp),
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
