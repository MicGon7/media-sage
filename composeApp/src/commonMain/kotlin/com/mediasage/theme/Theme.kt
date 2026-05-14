package com.mediasage.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider

// ── Classic (newspaper broadsheet) ───────────────────────────────────────────

internal fun classicLightColors(): ColorScheme = lightColorScheme(
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

internal fun classicDarkColors(): ColorScheme = darkColorScheme(
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

internal fun classicLightAppColors() = AppColors(
    accent = NavyLight,
    ruleLine = RuleLine,
    cardBorder = CardBorder,
)

internal fun classicDarkAppColors() = AppColors(
    accent = NavyMuted,
    ruleLine = RuleLineDark,
    cardBorder = RuleLineDark,
)

// ── Modern (brand navy — dark navy in both modes, deeper in dark) ─────────────

internal fun modernLightColors(): ColorScheme = darkColorScheme(
    primary = White,
    onPrimary = Navy,
    primaryContainer = NavyLight,
    onPrimaryContainer = InkLight,
    secondary = InkLight,
    onSecondary = Navy,
    secondaryContainer = Navy,
    onSecondaryContainer = InkLight,
    tertiary = ModernBorderMuted,
    onTertiary = Navy,
    background = Navy,
    onBackground = InkLight,
    surface = ModernLightCard,
    onSurface = InkLight,
    surfaceVariant = ModernLightCard,
    onSurfaceVariant = CharcoalLight,
    outline = ModernBorder,
    outlineVariant = ModernBorderMuted,
    error = ErrorDark,
    onError = Navy,
)

internal fun modernDarkColors(): ColorScheme = darkColorScheme(
    primary = White,
    onPrimary = NavyDeep,
    primaryContainer = Navy,
    onPrimaryContainer = InkLight,
    secondary = InkLight,
    onSecondary = NavyDeep,
    secondaryContainer = NavyDeep,
    onSecondaryContainer = InkLight,
    tertiary = ModernBorderMuted,
    onTertiary = NavyDeep,
    background = NavyDeep,
    onBackground = InkLight,
    surface = ModernDarkCard,
    onSurface = InkLight,
    surfaceVariant = ModernDarkCard,
    onSurfaceVariant = CharcoalLight,
    outline = ModernBorder,
    outlineVariant = ModernBorderMuted,
    error = ErrorDark,
    onError = NavyDeep,
)

internal fun modernLightAppColors() = AppColors(
    accent = White,
    ruleLine = ModernBorderMuted,
    cardBorder = ModernBorder,
)

internal fun modernDarkAppColors() = AppColors(
    accent = InkLight,
    ruleLine = ModernBorderMuted,
    cardBorder = ModernDarkCard,
)

// ── Future (Kindle e-reader — warm sepia light, warm dark night mode) ─────────

internal fun futureLightColors(): ColorScheme = lightColorScheme(
    primary = ReaderPrimary,
    onPrimary = White,
    primaryContainer = ReaderSurface,
    onPrimaryContainer = Ink,
    secondary = ReaderPrimary,
    onSecondary = White,
    secondaryContainer = ReaderSurface,
    onSecondaryContainer = Ink,
    tertiary = Slate,
    onTertiary = White,
    background = ReaderSurface,
    onBackground = Ink,
    surface = ReaderSurface,
    onSurface = Ink,
    surfaceVariant = ReaderSurface,
    onSurfaceVariant = Charcoal,
    outline = CardBorder,
    outlineVariant = RuleLine,
    error = Error,
    onError = White,
)

internal fun futureDarkColors(): ColorScheme = darkColorScheme(
    primary = ReaderAmber,
    onPrimary = DarkBackground,
    primaryContainer = DarkSurface,
    onPrimaryContainer = InkLight,
    secondary = ReaderAmber,
    onSecondary = DarkBackground,
    secondaryContainer = DarkSurface,
    onSecondaryContainer = InkLight,
    tertiary = SlateLight,
    onTertiary = DarkBackground,
    background = DarkSurface,
    onBackground = InkLight,
    surface = DarkSurface,
    onSurface = InkLight,
    surfaceVariant = DarkSurface,
    onSurfaceVariant = CharcoalLight,
    outline = RuleLineDark,
    outlineVariant = RuleLineDark,
    error = ErrorDark,
    onError = DarkSurface,
)

internal fun futureLightAppColors() = AppColors(
    accent = ReaderPrimary,
    ruleLine = ReaderSurface,
    cardBorder = ReaderSurface,
)

internal fun futureDarkAppColors() = AppColors(
    accent = ReaderAmber,
    ruleLine = RuleLineDark,
    cardBorder = DarkSurface,
)

// ── Composable ────────────────────────────────────────────────────────────────

@Composable
fun MediaSageTheme(
    theme: AppTheme = AppTheme.CLASSIC,
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme = when (theme) {
        AppTheme.CLASSIC -> if (darkTheme) classicDarkColors() else classicLightColors()
        AppTheme.MODERN -> if (darkTheme) modernDarkColors() else modernLightColors()
        AppTheme.FUTURE -> if (darkTheme) futureDarkColors() else futureLightColors()
    }
    val appColors = when (theme) {
        AppTheme.CLASSIC -> if (darkTheme) classicDarkAppColors() else classicLightAppColors()
        AppTheme.MODERN -> if (darkTheme) modernDarkAppColors() else modernLightAppColors()
        AppTheme.FUTURE -> if (darkTheme) futureDarkAppColors() else futureLightAppColors()
    }
    CompositionLocalProvider(LocalAppColors provides appColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = mediaSageTypography(),
            content = content,
        )
    }
}
