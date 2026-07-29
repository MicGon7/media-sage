# MS-679: Morning | Evening tabs on the past briefing screen

## What changed

`DayDetailScreen`'s two-briefing view used to be an accordion (`ExpandableBriefingSection`) — a
clickable header with the tone label and `ThemeChip`, expanding to a trimmed `MediaSageBriefingBody`.
That's replaced with a `MediaSageTabRow` (Morning | Evening) fixed at the bottom of the screen, and
each tab now renders the exact same `MediaSageBriefingCard` the daily `BriefingScreen` uses — full
header, theme chip, scripture, insight/implication/inspiration, and sources.

Days with only one briefing are unchanged: no tab row, the single card renders directly.

## Extracting the tab row

`FigureDetailScreen` already had a comic-palette bottom tab row (Biography | Quotes | Writings) —
gradient background that flips with dark/light mode, custom top+bottom indicator bars instead of
Material's default single underline. That composable took a `FigureDetailTab` enum directly, which
made it single-purpose. Pulled it out to `ui/MediaSageTabRow.kt` as:

```kotlin
fun MediaSageTabRow(
    selectedIndex: Int,
    tabLabels: List<String>,
    onTabSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
)
```

Index-based instead of generic-over-an-enum — the two call sites (`FigureDetailTab.entries`,
`DayDetailContract.BriefingSummary` tones) don't share a type, and a `List<String>` + `Int` keeps the
component ignorant of either one. `FigureDetailScreen` now maps its enum to labels/index at the call
site instead of owning the tab row.

## DayDetailScreen layout

Matched `FigureDetailContent`'s structure: scrollable content in a `Column(Modifier.weight(1f))`,
tab row below it outside the scroll, so the tabs stay pinned to the bottom instead of scrolling away.
Previously the whole screen was one scrollable `Column` with no bottom-anchored element.

## State: toggle → select

`DayDetailContract.UiState.Ready.expandedTone` (nullable — meant "which section is open, or none if
just collapsed") became `selectedTone` (non-null — a tab is always selected once there's more than one
briefing). `Intent.BriefingToggled` became `Intent.BriefingToneSelected`, and the ViewModel's toggle
logic (`if (current == intent.tone) null else intent.tone`) is now a plain `update { intent.tone }` —
tabs don't have a "both closed" state the way an accordion does.

`SingleBriefingContent` is reused for both the single-briefing case and the selected tab's content —
it already rendered the full `MediaSageBriefingCard`, so no new content composable was needed.
