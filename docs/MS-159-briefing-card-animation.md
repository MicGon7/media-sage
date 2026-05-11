# MS-159: Briefing Card Entrance Animation

## Problem

The Home screen briefing card popped in abruptly and pushed headlines down erratically. Two root causes:

1. **Item count change** — the `when (briefingCard)` block added and removed `LazyColumn` items entirely on state change, causing a hard layout reflow with no transition.
2. **Large shimmer** — `BriefingCardShimmer` reserved ~400dp of vertical space, blocking headlines for the full 7–9 second API call.

## What Changed

### `HomeScreen.kt`

**Consolidated briefing card slot** — replaced the multi-item `when` block with a single `item {}` containing `AnimatedContent`. All three states (Loading, Ready, Hidden) render inside the same slot, so the `LazyColumn` item count never changes.

```kotlin
item {
    AnimatedContent(
        targetState = briefingCard,
        transitionSpec = { fadeIn(tween(400)) togetherWith fadeOut(tween(200)) },
        label = "briefingCard",
        modifier = Modifier.animateContentSize(tween(300, easing = LinearEasing))
    ) { state ->
        when (state) {
            is BriefingCardState.Loading -> BriefingCardLoading()
            is BriefingCardState.Ready -> BriefingCard(state, onFigureTap)
            is BriefingCardState.Hidden -> Box(Modifier.fillMaxWidth())
        }
    }
}
```

`animateContentSize` smooths the height expansion as the card grows from the compact loader to the full card. `LinearEasing` keeps the expansion at a consistent speed (300ms).

**Replaced `BriefingCardShimmer` with `BriefingCardLoading`** — a compact ~56dp row with a "DAILY BRIEFING" label and a thin (2dp) `LinearProgressIndicator`. Headlines are visible immediately below it rather than being pushed off screen by a large skeleton.

**`HeadlinesFeed` Box** — added `Modifier.background(MaterialTheme.colorScheme.surface)` to prevent content bleed-through during overscroll and pull-to-refresh.

**`Masthead` padding** — reduced top padding from 12dp to 4dp. Uses the explicit four-parameter form since top and bottom differ:
```kotlin
.padding(start = 16.dp, top = 4.dp, end = 16.dp, bottom = 12.dp)
```

### `strings.xml`

Added `briefing_card_loading = "Daily Briefing"` — rendered as `.uppercase()` in the composable to match the category label style used in `HeadlineRow`.

## Key Design Decisions

### Why LinearEasing for animateContentSize

The default `tween` easing (`FastOutSlowInEasing`) produced an uneven expansion — perceptibly slow at the start, then rushing to finish. `LinearEasing` keeps a consistent pace across the full 300ms, which reads as intentional rather than glitchy.

### Why a compact loader instead of a shimmer

A shimmer reserves the full card height (~400dp) as a placeholder. This blocks nearly the entire screen for 7–9 seconds and provides no real value — the user can't interact with it. The compact loader communicates "something is loading here" with minimal vertical footprint, letting headlines render immediately below it.

### Why not prefetch

Prefetching (WorkManager / BGAppRefreshTask) would eliminate the loading state entirely but requires knowing the user's pinned figure before the UI renders. Once login is implemented, the pinned figure becomes a server-side preference tied to an authenticated session — prefetching must wait until that infrastructure exists.

### Padding chaining vs. explicit form

Compose's `.padding(horizontal, vertical)` shorthand only works when both horizontal values are equal and both vertical values are equal. When top and bottom differ, the explicit named-parameter form is required and preferred:
```kotlin
.padding(start = 16.dp, top = 4.dp, end = 16.dp, bottom = 12.dp)
```
Chaining multiple `.padding()` calls (e.g. `.padding(horizontal = 16.dp).padding(top = 4.dp)`) works but is discouraged — each call stacks a new layer, making accidental double-padding easy to introduce and hard to spot.

## Testing

```bash
./gradlew detekt  # clean
```

Manual smoke test:
- Cold launch → compact "DAILY BRIEFING" loader visible, headlines immediately below ✓
- Briefing loads → card fades in, height expands smoothly at constant speed ✓
- Pull-to-refresh (cache hit) → no loading state, briefing stays in place ✓
- Pull-to-refresh (tone change) → compact loader appears briefly, card fades in ✓
