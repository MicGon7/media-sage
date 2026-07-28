package com.mediasage.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

@Immutable
data class AppColors(
    val accent: Color,
    val ruleLine: Color,
    val cardBorder: Color,
    val backgroundBrush: Brush? = null,
)

val LocalAppColors = staticCompositionLocalOf {
    AppColors(
        accent = Color.Unspecified,
        ruleLine = Color.Unspecified,
        cardBorder = Color.Unspecified,
    )
}

/** The app's actual dark-mode state — an explicit in-app settings toggle, not the OS-level
 * setting `isSystemInDarkTheme()` reports. Provided once in [MediaSageTheme] from the real
 * `darkTheme` parameter; read this instead of re-deriving dark mode per component. */
val LocalIsDarkMode = staticCompositionLocalOf { false }

object MediaSageTheme {
    val colors: AppColors
        @Composable get() = LocalAppColors.current

    val isDark: Boolean
        @Composable get() = LocalIsDarkMode.current
}
