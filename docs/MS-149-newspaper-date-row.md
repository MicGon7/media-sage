# MS-149: Newspaper-Style Date Row on Home Screen

## What Was Built

Added a `NewspaperDateRow` composable to `HomeScreen.kt` that renders between the masthead and the briefing card. It displays three columns spanning the full width:

- **Left**: `Est. 2026` — left-aligned
- **Center**: Current date formatted as `Thursday, May 7, 2026` — centered
- **Right**: `Price J3:16` — right-aligned

A `HorizontalDivider` (primary color, 1dp — matching the masthead divider) appears below the row before the briefing card.

## Design Decisions

### Three-column layout with `weight`

Used a `Row` with three `Text` composables, each assigned a `Modifier.weight()`. The center date gets `weight(2f)` and the flanking labels each get `weight(1f)`. This guarantees the date is always centered relative to the full row width regardless of text length.

### Date formatting without a formatter class

`kotlinx-datetime` 0.6.1 does not include a `DateTimeFormatter`. The date is assembled manually:

```kotlin
val today = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
val dayName = today.dayOfWeek.name.lowercase().replaceFirstChar { it.uppercase() }
val monthName = today.month.name.lowercase().replaceFirstChar { it.uppercase() }
val dateText = "$dayName, $monthName ${today.dayOfMonth}, ${today.year}"
```

`DayOfWeek` and `Month` enum `.name` values are uppercase (e.g., `THURSDAY`, `MAY`). `lowercase().replaceFirstChar { it.uppercase() }` converts them to title case. `dayOfMonth` is an `Int` with no leading zero, which matches the target format.

### No string resources for UI constants

`Est. 2026` and `Price J3:16` are editorial constants, not localizable UI text. The ticket explicitly called for no string resources for these values — they live as inline literals in the composable.

### `LazyColumn` item grouping

The `NewspaperDateRow` and its trailing `HorizontalDivider` are placed inside a single `item { }` block in `HeadlinesFeed`. This keeps them coupled and avoids a scroll gap between the two.

## Files Changed

- `composeApp/src/commonMain/kotlin/com/mediasage/feature/home/HomeScreen.kt`
  - Added `NewspaperDateRow` composable
  - Added item in `HeadlinesFeed` between `Masthead()` and the briefing card
  - Added imports: `TextAlign`, `Clock`, `TimeZone`, `toLocalDateTime`
