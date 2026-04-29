package com.mediasage.theme

import androidx.compose.foundation.isSystemInDarkTheme
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
    onPrimary = DarkBackground,
    primaryContainer = Navy,
    onPrimaryContainer = InkLight,
    secondary = NavyLight,
    onSecondary = DarkBackground,
    secondaryContainer = Navy,
    onSecondaryContainer = InkLight,
    tertiary = SlateLight,
    onTertiary = DarkBackground,
    background = DarkBackground,
    onBackground = InkLight,
    surface = DarkSurface,
    onSurface = InkLight,
    surfaceVariant = Ink,
    onSurfaceVariant = CharcoalLight,
    outline = RuleLineDark,
    outlineVariant = Slate,
    error = ErrorDark,
    onError = DarkBackground,
)

@Composable
fun MediaSageTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = mediaSageTypography(),
        content = content,
    )
}
