# MS-149: Newspaper-Style Date Row on Home Screen

## What Was Built

Added a `NewspaperDateRow` composable to `HomeScreen.kt` that renders a centered dateline between the masthead and the briefing card. The row owns both its bounding dividers (primary color, 1dp) so the date sits cleanly between them as a self-contained editorial element.

Also replaced the duplicate private `LoadingState` composable with the shared `MediaSageLoadingState` and added a `HeadlinesFeedPreview`.

## Design Decisions

### `Box` with alignment anchors, not `Row` with weights

The first attempt used a `Row` with `weight(1f) / weight(2f) / weight(1f)` for the three columns. This produces visual asymmetry: if the side labels are shorter than the center text, the center column shifts off-center because `weight` divides available space, not anchors to the container edge.

The correct approach is `Box` with `Alignment.CenterStart`, `Alignment.Center`, and `Alignment.CenterEnd`. Each text is independently anchored to its edge or the center of the container — true three-column centering regardless of text lengths.

```kotlin
Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
    Text(text = "Est. 2026", modifier = Modifier.align(Alignment.CenterStart))
    Text(text = dateText,   modifier = Modifier.align(Alignment.Center))
    Text(text = "Price J3:16", modifier = Modifier.align(Alignment.CenterEnd))
}
```

**Rule:** Use `Box` + alignment anchors when each element needs to be independently positioned. Use `Row` + weights only when you want proportional space distribution.

### Composable owns its own dividers

Rather than placing `HorizontalDivider`s in the call site (one inside `Masthead`, one after `NewspaperDateRow` in `HeadlinesFeed`), the component owns both its top and bottom dividers inside a `Column`. This makes the visual boundary an implementation detail of the component and simplifies the `LazyColumn` call site to a single `item { NewspaperDateRow() }`.

### Text style matches briefing card figure name

`titleMedium` + `FontWeight.Bold` — the same style used for the figure name in `BriefingCard`. This gives the dateline enough visual weight to read clearly between two dividers without competing with the masthead above it.

### Date formatting without a formatter class

`kotlinx-datetime` does not include a `DateTimeFormatter`. The date is assembled manually from enum names:

```kotlin
val today = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
val dayName = today.dayOfWeek.name.lowercase().replaceFirstChar { it.uppercase() }
val monthName = today.month.name.lowercase().replaceFirstChar { it.uppercase() }
val dateText = "$dayName, $monthName ${today.dayOfMonth}, ${today.year}"
```

`DayOfWeek` and `Month` `.name` are uppercase enums (e.g. `THURSDAY`, `MAY`). `lowercase().replaceFirstChar { it.uppercase() }` converts to title case. `dayOfMonth` is an `Int` with no leading zero.

### Reuse `MediaSageLoadingState`

HomeScreen had a private `LoadingState` composable identical to `MediaSageLoadingState` in the shared `ui/` package. Removed the duplicate and used the shared component directly.

## Files Changed

- `composeApp/src/commonMain/kotlin/com/mediasage/feature/home/HomeScreen.kt`
  - Added `NewspaperDateRow` composable with owned top/bottom dividers
  - Replaced `LoadingState` with `MediaSageLoadingState`
  - Removed unused `Color` import
  - Added `HeadlinesFeedPreview`
