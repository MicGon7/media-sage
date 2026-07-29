package com.mediasage.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.mediasage.theme.ComicGradientOrientation
import com.mediasage.theme.MediaSageTheme
import com.mediasage.theme.rememberComicSurfaceColors

/**
 * A clickable [Surface] styled after the app's comic / vintage-newspaper illustration, shared by
 * [MediaSageEntryCard], [MediaSageComicChip], and `PastBriefingCard`. Renders the dark/light
 * adaptive comic-palette background from [rememberComicSurfaceColors] and hands the matching
 * content color to [content] for its text/icon tints — an optional border (chips only) is drawn
 * on the [Surface] itself, since it sits outside the background this wraps.
 */
@Composable
fun MediaSageSurface(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    shape: Shape = MaterialTheme.shapes.large,
    orientation: ComicGradientOrientation = ComicGradientOrientation.Vertical,
    bordered: Boolean = false,
    shadowElevation: Dp = 0.dp,
    content: @Composable (contentColor: Color) -> Unit,
) {
    val comicColors = rememberComicSurfaceColors(orientation)
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = shape,
        color = Color.Transparent,
        border = if (bordered) BorderStroke(1.dp, comicColors.border) else null,
        shadowElevation = shadowElevation,
    ) {
        Box(modifier = comicColors.background) {
            content(comicColors.content)
        }
    }
}

// region Previews

@Preview(showBackground = true)
@Composable
private fun MediaSageSurfacePreview() {
    MediaSageTheme {
        MediaSageSurface(onClick = {}, shadowElevation = 2.dp) { contentColor ->
            Text(text = "Comic surface", color = contentColor, modifier = Modifier.padding(16.dp))
        }
    }
}

// endregion
