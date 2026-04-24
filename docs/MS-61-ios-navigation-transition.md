# MS-61: Fix iOS Navigation Transition

## Problem

On iOS, navigating between screens showed the previous screen's content bleeding through during the slide transition, making it look buggy.

## Root Cause

Composable screens had no opaque background — without a `Surface` wrapper they are transparent by default. During a slide, the outgoing screen was visible beneath the incoming one.

## Fix

Added `Surface(modifier = Modifier.fillMaxSize())` as the root wrapper in all three screens:
- `HomeScreen.kt`
- `MatchScreen.kt`
- `FiguresScreen.kt`

Platform default transitions (slide on iOS, fade on Android) are now preserved since the bleed was caused by transparency, not the transition itself.

## Key Learnings

### Always wrap screens in Surface

In Compose, composables are transparent by default. Any screen used in navigation should have `Surface(modifier = Modifier.fillMaxSize())` at its root to ensure an opaque background during transitions.

### Nav3 transitionSpec Scene.key

Attempted to differentiate top-level tab switches (fade) from detail navigation (slide) via `targetState.key` in `transitionSpec`, but `Scene.key` did not reliably match route instances. Tracked in MS-62 for future investigation.
