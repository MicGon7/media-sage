package com.mediasage.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val LightColorScheme = lightColorScheme(
    primary = Navy,
    onPrimary = White,
    primaryContainer = NavyLight,
    onPrimaryContainer = White,
    secondary = NavyLight,
    onSecondary = White,
    secondaryContainer = OffWhite,
    onSecondaryContainer = Navy,
    tertiary = Slate,
    onTertiary = White,
    background = White,
    onBackground = Ink,
    surface = White,
    onSurface = Ink,
    surfaceVariant = OffWhite,
    onSurfaceVariant = Charcoal,
    outline = CardBorder,
    outlineVariant = RuleLine,
    error = Error,
    onError = White,
)

private val DarkColorScheme = darkColorScheme(
    primary = NavyMuted,
    onPrimary = Charcoal,
    primaryContainer = Navy,
    onPrimaryContainer = InkLight,
    secondary = NavyLight,
    onSecondary = Charcoal,
    secondaryContainer = Navy,
    onSecondaryContainer = InkLight,
    tertiary = SlateLight,
    onTertiary = Charcoal,
    background = Charcoal,
    onBackground = InkLight,
    surface = Charcoal,
    onSurface = InkLight,
    surfaceVariant = Ink,
    onSurfaceVariant = CharcoalLight,
    outline = RuleLineDark,
    outlineVariant = Slate,
    error = ErrorDark,
    onError = Charcoal,
)

@Composable
fun MediaSageTheme(
    @Suppress("UNUSED_PARAMETER")
    darkTheme: Boolean = false, // TODO MS-30: wire up dark theme
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = mediaSageTypography(),
        content = content,
    )
}
