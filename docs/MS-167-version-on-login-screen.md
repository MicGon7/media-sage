# MS-167: Show App Version on Login Screen

## What was built

Small muted version text (e.g. `v1.0 (build 1)`) anchored to the bottom-center of the login
screen gradient background. Visible on both Android and iOS.

## Pattern used: CompositionLocal for ambient build-time values

Version info is an ambient environment value, not runtime UI state. Following the same pattern
as `LocalIsDebugBuild`, a new `LocalAppVersion` CompositionLocal carries the formatted version
string from each platform's entry point down the tree without threading it through every
intermediate composable.

```
Android: MainActivity  → BuildConfig.VERSION_NAME + VERSION_CODE → App(appVersion=...)
   iOS: ContentView.swift → Bundle.main.infoDictionary["CFBundleShortVersionString" / "CFBundleVersion"] → MainViewController(appVersion:...) → App(appVersion=...)
        ↓
        App.kt: CompositionLocalProvider(LocalAppVersion provides appVersion)
        ↓
        LoginScreen: LocalAppVersion.current → Text(...) anchored at BottomCenter
```

## Why not UiState or ViewModel?

CLAUDE.md is explicit: "UiState holds UI state, not build config." Version string is a
compile-time constant — it never changes at runtime. Using a ViewModel or adding it to
`LoginContract.UiState` would create unnecessary coupling and contradict the project's own
stated principle.

## Why not expect/actual?

CLAUDE.md warns against `expect/actual` for build config constants: it creates duplicate class
entries in the Android dex and causes `NoSuchMethodError` crashes from stale build cache
artifacts. The correct pattern is to read the value at the platform entry point (Swift / Kotlin
Activity) and pass it as a parameter into `App()`.

## iOS version source

- `CFBundleShortVersionString` → marketing version (matches `MARKETING_VERSION` in
  `Config.xcconfig`, currently `1.0`)
- `CFBundleVersion` → build number (matches `CURRENT_PROJECT_VERSION`, currently `1`)

## Visual treatment

Inside `LoginScreenContent`, the version text is a second child of the `Box` (sibling of the
centered Column). Using `Modifier.align(Alignment.BottomCenter)` pins it to the bottom without
disturbing the Column's vertical centering. Style: `labelSmall` with `OnGradientMuted` color —
the same muted tint used for other secondary decorative elements on the login screen.
