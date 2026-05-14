# MS-171 — App-Wide Theme Variants: Classic, Modern, and Future

## What was built

Three named theme variants were added to the app, each supporting both light and dark mode. Theme selection is currently programmatic — a `val appTheme = AppTheme.CLASSIC` constant in `App.kt`. A future Settings screen ticket will wire it to a user preference.

## Pattern: Extending MaterialTheme

The implementation follows the official [Compose custom design system guidance](https://developer.android.com/develop/ui/compose/designsystems/custom): keep Material as the foundation for component compatibility, but wrap it in a `CompositionLocalProvider` that supplies a custom `AppColors` type for semantic tokens that Material's `ColorScheme` doesn't have (`accent`, `ruleLine`, `cardBorder`).

This is the same pattern used by MaterialTheme itself — `MaterialTheme` is both a composable function and an object that exposes `MaterialTheme.colorScheme`, `MaterialTheme.typography`, etc. We mirror this exactly:

```kotlin
// AppColors.kt
object MediaSageTheme {
    val colors: AppColors
        @Composable get() = LocalAppColors.current
}

// Theme.kt
fun MediaSageTheme(theme: AppTheme = AppTheme.CLASSIC, ...) {
    CompositionLocalProvider(LocalAppColors provides appColors) {
        MaterialTheme(colorScheme = colorScheme, ...) { ... }
    }
}
```

Call sites use `MediaSageTheme.colors.accent` for custom tokens and `MaterialTheme.colorScheme.primary` for standard Material tokens.

## Files changed

| File | Change |
|------|--------|
| `theme/AppTheme.kt` | New — `enum class AppTheme { CLASSIC, MODERN, FUTURE }` |
| `theme/AppColors.kt` | New — `AppColors` data class, `LocalAppColors` CompositionLocal, `MediaSageTheme` object |
| `theme/Theme.kt` | Rewritten — six color scheme functions + six AppColors functions + updated `MediaSageTheme` composable |
| `theme/Color.kt` | Additions only — Modern and Future palette colors |
| `theme/ThemePreview.kt` | New — six `@Preview` composables (one per theme × mode) |
| `App.kt` | Added `val appTheme = AppTheme.CLASSIC` control point; updated `MediaSageTheme` call |

## Theme palette summary

### Classic (newspaper broadsheet)
- **Light**: Navy primary, White background, Ink text, RuleLine dividers
- **Dark**: NavyMuted primary, DarkBackground (warm brown `0xFF1C1A14`), InkLight text

### Modern (editorial magazine)
- **Light**: NavyLight primary, Accent gold (`0xFFD4A853`) secondary, clean White surface
- **Dark**: NavyLight primary, ModernDark (`0xFF1A1E2E`) background, AccentDark secondary

### Future (clean digital)
- **Light**: ElectricBlue (`0xFF4A9EFF`) primary, FutureBackground (`0xFFF8F9FF`) background, minimal borders
- **Dark**: ElectricBlue primary, FutureDark (`0xFF0D0D0F`) background, high contrast white text

## Key design decision: function + object with the same name

Kotlin allows a top-level function and an object to share the same name in the same package. This is how MaterialTheme itself works. `MediaSageTheme { ... }` calls the composable function; `MediaSageTheme.colors.accent` accesses the object. Existing call sites that use `MediaSageTheme { ... }` or `MediaSageTheme(darkTheme = true) { ... }` continue to compile unchanged because `theme: AppTheme = AppTheme.CLASSIC` has a default value.

## Backward compatibility

All existing screen previews that call `MediaSageTheme { ... }` (without a `theme` parameter) continue to work, defaulting to `AppTheme.CLASSIC` — preserving the existing look unchanged.

## Switching themes

To switch the whole app to a different theme, change the constant in `App.kt`:

```kotlin
val appTheme = AppTheme.MODERN  // or AppTheme.FUTURE
```

The Settings screen (future ticket) will replace this constant with a value read from `ThemePreferencesRepository`.
