# MS-61: Fix iOS Navigation Transition

## Problem

On iOS, `NavDisplay` defaulted to a slide transition while Android used a fade. The slide itself was acceptable but the previous screen's content bled into the incoming screen during the transition, making it look buggy.

## Fix

Added `transitionSpec` and `popTransitionSpec` to `NavDisplay` in `MediaSageScaffold.kt`:

```kotlin
NavDisplay(
    backStack = appState.backStack,
    modifier = Modifier.padding(padding),
    transitionSpec = { fadeIn() togetherWith fadeOut() },
    popTransitionSpec = { fadeIn() togetherWith fadeOut() },
) { route -> ... }
```

This applies a cross-fade on both forward and back navigation, consistent across Android and iOS.

## Key Learnings

### Nav3 Transition API

`NavDisplay` in Nav3 1.0.0-alpha05 uses `transitionSpec` and `popTransitionSpec` (not `enterTransition`/`exitTransition`). Both accept `AnimatedContentTransitionScope<Scene<T>>.() -> ContentTransform`.

### iOS Server URL Config

`ProcessInfo.processInfo.environment` only receives Xcode scheme env vars when launched via Xcode's Run button — not when tapping the app icon. For local dev on a physical device, hardcoding the IP in `MainViewController.kt` is the pragmatic solution. In production this is a non-issue as the URL will be a real domain.
