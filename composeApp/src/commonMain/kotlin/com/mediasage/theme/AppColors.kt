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
    val isDark: Boolean,
    val backgroundBrush: Brush? = null,
)

val LocalAppColors = staticCompositionLocalOf {
    AppColors(
        accent = Color.Unspecified,
        ruleLine = Color.Unspecified,
        cardBorder = Color.Unspecified,
        isDark = false,
    )
}

object MediaSageTheme {
    val colors: AppColors
        @Composable get() = LocalAppColors.current
}
