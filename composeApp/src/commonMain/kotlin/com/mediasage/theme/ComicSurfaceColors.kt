package com.mediasage.theme

import androidx.compose.foundation.background
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** Orientation of the light-mode sepia gradient — cards run it top-to-bottom, chips left-to-right. */
enum class ComicGradientOrientation { Vertical, Horizontal }

/**
 * A clickable surface's dark/light-adaptive comic-palette colors: fixed sepia gradient background
 * + [ComicInk] content/border in light mode, versus a neutral elevated [MaterialTheme.colorScheme]
 * surface + [MaterialTheme.colorScheme.onSurface]/`outline` in dark mode. A fixed warm tone (e.g.
 * [ComicTan]) would clash if paired with the neutral dark surface instead of the light-mode gradient.
 */
@Immutable
data class ComicSurfaceColors(
    val background: Modifier,
    val content: Color,
    val border: Color,
)

@Composable
fun rememberComicSurfaceColors(
    orientation: ComicGradientOrientation = ComicGradientOrientation.Vertical,
): ComicSurfaceColors {
    val isDark = MediaSageTheme.isDark
    val content = if (isDark) MaterialTheme.colorScheme.onSurface else ComicInk
    val border = if (isDark) MaterialTheme.colorScheme.outline else ComicBrown
    val background = if (isDark) {
        Modifier.background(MaterialTheme.colorScheme.surfaceColorAtElevation(4.dp))
    } else {
        val gradient = when (orientation) {
            ComicGradientOrientation.Vertical -> Brush.verticalGradient(colors = listOf(ComicCream, ComicTan))
            ComicGradientOrientation.Horizontal -> Brush.horizontalGradient(colors = listOf(ComicCream, ComicTan))
        }
        Modifier.background(gradient)
    }
    return ComicSurfaceColors(background = background, content = content, border = border)
}
