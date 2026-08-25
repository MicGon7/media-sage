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
    surfaceTint = Navy,
)

internal fun classicDarkColors(): ColorScheme = darkColorScheme(
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
    surfaceTint = ReaderAmber,
)

internal fun classicLightAppColors() = AppColors(
    accent = NavyLight,
    ruleLine = RuleLine,
    cardBorder = CardBorder,
)

internal fun classicDarkAppColors() = AppColors(
    accent = ReaderAmber,
    ruleLine = RuleLineDark,
    cardBorder = DarkSurface,
)

// ── Modern (mockup — near-black dark with amber accent, warm cream light) ────

internal fun modernLightColors(): ColorScheme = lightColorScheme(
    primary = ModernAmber,
    onPrimary = White,
    primaryContainer = ModernLightCard,
    onPrimaryContainer = ModernLightText,
    secondary = ModernAmber,
    onSecondary = White,
    secondaryContainer = ModernLightCard,
    onSecondaryContainer = ModernLightText,
    tertiary = ModernLightMuted,
    onTertiary = White,
    background = White,
    onBackground = ModernLightText,
    surface = ModernLightCard,
    onSurface = ModernLightText,
    surfaceVariant = ModernLightCard,
    onSurfaceVariant = ModernLightMuted,
    outline = ModernLightBorder,
    outlineVariant = ModernLightBorder,
    error = Error,
    onError = White,
    surfaceTint = ModernAmber,
)

internal fun modernDarkColors(): ColorScheme = darkColorScheme(
    primary = ModernAmber,
    onPrimary = ModernDarkBg,
    primaryContainer = ModernDarkCard,
    onPrimaryContainer = ModernDarkText,
    secondary = ModernAmber,
    onSecondary = ModernDarkBg,
    secondaryContainer = ModernDarkCard,
    onSecondaryContainer = ModernDarkText,
    tertiary = ModernDarkMuted,
    onTertiary = ModernDarkBg,
    background = ModernDarkBg,
    onBackground = ModernDarkText,
    surface = ModernDarkCard,
    onSurface = ModernDarkText,
    surfaceVariant = ModernDarkCard,
    onSurfaceVariant = ModernDarkMuted,
    outline = ModernDarkBorder,
    outlineVariant = ModernDarkBorder,
    error = ErrorDark,
    onError = ModernDarkBg,
    surfaceTint = ModernAmber,
)

internal fun modernLightAppColors() = AppColors(
    accent = ModernAmber,
    ruleLine = ModernLightBorder,
    cardBorder = ModernLightBorder,
)

internal fun modernDarkAppColors() = AppColors(
    accent = ModernAmber,
    ruleLine = ModernDarkBorder,
    cardBorder = ModernDarkCard,
)

// ── Warm (Kindle e-reader — warm sepia light, warm dark night mode) ──────────

internal fun warmLightColors(): ColorScheme = lightColorScheme(
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
    surfaceTint = ReaderPrimary,
)

internal fun warmDarkColors(): ColorScheme = darkColorScheme(
    primary = ReaderAmber,
    onPrimary = WarmDarkBg,
    primaryContainer = WarmDarkSurface,
    onPrimaryContainer = ReaderSurface,
    secondary = ReaderAmber,
    onSecondary = WarmDarkBg,
    secondaryContainer = WarmDarkSurface,
    onSecondaryContainer = ReaderSurface,
    tertiary = WarmDarkMuted,
    onTertiary = WarmDarkBg,
    background = WarmDarkBg,
    onBackground = ReaderSurface,
    surface = WarmDarkSurface,
    onSurface = ReaderSurface,
    surfaceVariant = WarmDarkSurface,
    onSurfaceVariant = WarmDarkMuted,
    outline = WarmDarkBorder,
    outlineVariant = WarmDarkBorder,
    error = ErrorDark,
    onError = WarmDarkBg,
    surfaceTint = ReaderAmber,
)

internal fun warmLightAppColors() = AppColors(
    accent = ReaderPrimary,
    ruleLine = ReaderSurface,
    cardBorder = ReaderSurface,
)

internal fun warmDarkAppColors() = AppColors(
    accent = ReaderAmber,
    ruleLine = WarmDarkBorder,
    cardBorder = WarmDarkSurface,
)

// ── Composable ────────────────────────────────────────────────────────────────

@Composable
fun MediaSageTheme(
    theme: AppTheme = AppTheme.CLASSIC,
    darkTheme: Boolean = isSystemInDarkTheme(),
    textScalePercent: Int = 100,
    content: @Composable () -> Unit,
) {
    val colorScheme = when (theme) {
        AppTheme.CLASSIC -> if (darkTheme) classicDarkColors() else classicLightColors()
        AppTheme.MODERN -> if (darkTheme) modernDarkColors() else modernLightColors()
        AppTheme.WARM -> if (darkTheme) warmDarkColors() else warmLightColors()
    }
    val appColors = when (theme) {
        AppTheme.CLASSIC -> if (darkTheme) classicDarkAppColors() else classicLightAppColors()
        AppTheme.MODERN -> if (darkTheme) modernDarkAppColors() else modernLightAppColors()
        AppTheme.WARM -> if (darkTheme) warmDarkAppColors() else warmLightAppColors()
    }
    CompositionLocalProvider(LocalAppColors provides appColors, LocalIsDarkMode provides darkTheme) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = mediaSageTypography(scale = textScalePercent / 100f),
            content = content,
        )
    }
}
