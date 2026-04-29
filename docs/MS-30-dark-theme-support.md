# MS-30: Dark Theme Support

## What Changed

Wired up the dark color scheme in `MediaSageTheme` to respond to the system dark mode setting. The dark palette and `DarkColorScheme` existed since MS-36 but were never activated — the `darkTheme` parameter was always ignored.

Two files changed:

| File | Change |
|------|--------|
| `theme/Color.kt` | Added `DarkBackground` and `DarkSurface` — warm dark tones for newspaper aesthetic |
| `theme/Theme.kt` | Connected `darkTheme` to `isSystemInDarkTheme()`; removed suppression annotation and TODO |

## Dark Color Palette

The newspaper aesthetic in dark mode uses warm dark browns (aged newsprint) rather than neutral grays:

| Role | Color | Hex | Note |
|------|-------|-----|------|
| `background` | `DarkBackground` | `#1C1A14` | Very dark warm brown — the canvas |
| `surface` | `DarkSurface` | `#25221A` | Slightly lighter for cards and sheets |
| `surfaceVariant` | `Ink` | `#1A1A1A` | Neutral dark for variant surfaces |
| `onBackground` / `onSurface` | `InkLight` | `#F5F0E8` | Warm cream text — parchment feel |
| `primary` | `NavyMuted` | `#6B85A8` | Lightened navy for dark mode legibility |
| `outline` | `RuleLineDark` | `#404040` | Subtle dividers |

The key insight: `InkLight` (#F5F0E8) as text on `DarkBackground` (#1C1A14) creates the feel of warm typography on aged newsprint — the same editorial mood as light mode but inverted.

## How `isSystemInDarkTheme()` Works

`isSystemInDarkTheme()` is a `@Composable` function from `androidx.compose.foundation`. In Compose Multiplatform:

- **Android**: reads `Configuration.UI_MODE_NIGHT_MASK`
- **iOS**: reads `UITraitCollection.userInterfaceStyle`

It recomposes automatically when the system setting changes, so no manual toggle wiring is needed. The activity recreates on configuration change, which also re-calls `enableEdgeToEdge()` in `MainActivity`, correctly updating system bar icon colors.

## Edge-to-Edge System Bar Colors

`MainActivity` calls `enableEdgeToEdge()` with no arguments, which sets transparent system bars with `SystemBarStyle.auto()`. This auto-detects the night mode state and sets:
- Light mode → dark status bar icons on light background
- Dark mode → light status bar icons on dark background

Since `enableEdgeToEdge()` is called before `setContent`, and the activity recreates on dark mode toggle, system bar icon colors are always correct.

## Scaffold Colors

No changes were needed in `MediaSageScaffold.kt` — all colors already reference `MaterialTheme.colorScheme.*`:

- `Scaffold` → `containerColor = MaterialTheme.colorScheme.background`
- `NavigationBar` → `containerColor = MaterialTheme.colorScheme.surface`
- `NavigationBarItem` icons/text → `onSurface` / `onSurfaceVariant`

These automatically resolve to the dark scheme values when `darkTheme = true`.

## Screenshots

Screenshots comparing light and dark mode should be added here after manual verification on device. The color palette above documents the expected rendering.

**Light mode**: White canvas (`#FFFFFF`), Ink text (`#1A1A1A`), Navy accents (`#1B2A4A`)

**Dark mode**: Warm dark brown canvas (`#1C1A14`), cream text (`#F5F0E8`), muted navy accents (`#6B85A8`)

## Design Decisions

- **Warm dark over neutral gray**: `Charcoal` (`#2A2A2A`) was the original dark background — a neutral near-black. Replaced with `DarkBackground` (`#1C1A14`) and `DarkSurface` (`#25221A`), which have a warm brown undertone matching the editorial newspaper palette.
- **Cream text**: `InkLight` (`#F5F0E8`) reads as aged parchment on dark — consistent with the "New Life Times" newspaper brand identity.
- **No forced dark mode flag**: Removed `@Suppress("UNUSED_PARAMETER")` and the hardcoded `false` default — the theme now follows the system setting honestly.

## Related Tickets

- MS-36: Original newspaper theme (light mode, dark palette defined but deferred)
- MS-33: Home screen layout
- MS-34: Match screen layout
- MS-35: Voices screen layout
