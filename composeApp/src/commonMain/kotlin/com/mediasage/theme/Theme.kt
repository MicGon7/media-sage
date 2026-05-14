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

// ── Modern (editorial magazine) ───────────────────────────────────────────────

internal fun modernLightColors(): ColorScheme = lightColorScheme(
    primary = NavyLight,
    onPrimary = White,
    primaryContainer = Navy,
    onPrimaryContainer = White,
    secondary = Accent,
    onSecondary = White,
    secondaryContainer = OffWhite,
    onSecondaryContainer = Navy,
    tertiary = Slate,
    onTertiary = White,
    background = White,
    onBackground = Ink,
    surface = OffWhite,
    onSurface = Ink,
    surfaceVariant = OffWhite,
    onSurfaceVariant = Charcoal,
    outline = CardBorder,
    outlineVariant = RuleLine,
    error = Error,
    onError = White,
)

internal fun modernDarkColors(): ColorScheme = darkColorScheme(
    primary = NavyLight,
    onPrimary = ModernDark,
    primaryContainer = Navy,
    onPrimaryContainer = InkLight,
    secondary = AccentDark,
    onSecondary = ModernDark,
    secondaryContainer = Navy,
    onSecondaryContainer = InkLight,
    tertiary = SlateLight,
    onTertiary = ModernDark,
    background = ModernDark,
    onBackground = InkLight,
    surface = ModernSurface,
    onSurface = InkLight,
    surfaceVariant = Ink,
    onSurfaceVariant = CharcoalLight,
    outline = RuleLineDark,
    outlineVariant = Slate,
    error = ErrorDark,
    onError = ModernDark,
)

internal fun modernLightAppColors() = AppColors(
    accent = Accent,
    ruleLine = RuleLine,
    cardBorder = CardBorder,
)

internal fun modernDarkAppColors() = AppColors(
    accent = AccentDark,
    ruleLine = RuleLineDark,
    cardBorder = ModernSurface,
)

// ── Future (clean digital) ────────────────────────────────────────────────────

internal fun futureLightColors(): ColorScheme = lightColorScheme(
    primary = ElectricBlue,
    onPrimary = White,
    primaryContainer = FutureSurface,
    onPrimaryContainer = Ink,
    secondary = ElectricBlue,
    onSecondary = White,
    secondaryContainer = FutureSurface,
    onSecondaryContainer = Ink,
    tertiary = Slate,
    onTertiary = White,
    background = FutureBackground,
    onBackground = Ink,
    surface = FutureSurface,
    onSurface = Ink,
    surfaceVariant = FutureSurface,
    onSurfaceVariant = Charcoal,
    outline = FutureSurface,
    outlineVariant = FutureSurface,
    error = Error,
    onError = White,
)

internal fun futureDarkColors(): ColorScheme = darkColorScheme(
    primary = ElectricBlue,
    onPrimary = FutureDark,
    primaryContainer = FutureDarkSurface,
    onPrimaryContainer = White,
    secondary = ElectricBlue,
    onSecondary = FutureDark,
    secondaryContainer = FutureDarkSurface,
    onSecondaryContainer = White,
    tertiary = SlateLight,
    onTertiary = FutureDark,
    background = FutureDark,
    onBackground = White,
    surface = FutureDarkSurface,
    onSurface = White,
    surfaceVariant = FutureDarkSurface,
    onSurfaceVariant = CharcoalLight,
    outline = FutureDarkSurface,
    outlineVariant = FutureDarkSurface,
    error = ErrorDark,
    onError = FutureDark,
)

internal fun futureLightAppColors() = AppColors(
    accent = ElectricBlue,
    ruleLine = FutureSurface,
    cardBorder = FutureSurface,
)

internal fun futureDarkAppColors() = AppColors(
    accent = ElectricBlue,
    ruleLine = FutureDarkSurface,
    cardBorder = FutureDarkSurface,
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
