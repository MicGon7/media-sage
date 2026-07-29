# Gating the Login Bypass Behind a Debug-Only Triple-Tap

## What was built

`LoginScreen`'s bypass button is now:
1. Only composed when `LocalIsDebugBuild.current` is true (existing pattern, see `docs/MS-167-version-on-login-screen.md`) — unreachable in release/TestFlight builds regardless of interaction.
2. Gated behind a triple-tap gesture within a 600ms window (`TripleTapDetector`) even in debug builds — a stray single or double tap on the button no longer skips sign-in.

## Why `runComposeUiTest` doesn't work here

This project's `ui-test` guidance points at `runComposeUiTest` (Compose Multiplatform's `commonTest` interaction-testing API) for exercising tap sequences. On this repo's `composeApp` Android unit test target, `runComposeUiTest` throws `NullPointerException: Cannot invoke "String.toLowerCase(java.util.Locale)" because "android.os.Build.FINGERPRINT" is null` inside AndroidX test infrastructure during `setContent` — it requires a Robolectric shadow environment that only exists behind `@RunWith(AndroidJUnit4::class)`, which CLAUDE.md explicitly bans from `commonTest` (tests there must run on every platform, not just Android/Robolectric).

There is no fix available within those constraints: `runComposeUiTest` is not viable for interaction testing (taps, gestures, timing) in `commonTest` on this target.

## Resolution: extract gesture/timing logic to a plain Kotlin class

`TripleTapDetector` (`composeApp/src/commonMain/kotlin/com/mediasage/feature/login/TripleTapDetector.kt`) holds the tap-count/time-window logic with zero Compose or Android dependency — just `kotlin.time`. `LoginScreen`'s `rememberTripleTapAction` composable becomes a thin `remember { TripleTapDetector(...) }` wrapper. This lets `TripleTapDetectorTest.kt` cover the triple-tap behavior (triggers on third tap, single/double tap don't trigger, a tap outside the window resets the count) with plain `kotlin.test` + `TestTimeSource` — real unit tests, not Robolectric-dependent Compose UI tests.

**Takeaway for future tickets**: any UI change whose correctness hinges on gesture sequencing or timing (not just static state) should extract that logic into a plain class before reaching for `runComposeUiTest` — it is both more portable (`commonTest`, all platforms) and, on this repo's current target configuration, the only thing that actually works.

## Roborazzi capture caveat for tall screens

The login screen's Roborazzi capture at the project's default Robolectric viewport (320×470dp, `w320dp-h470dp` per `@Config`) doesn't fit the full form — the bypass button sits below the fold, so the debug and release captures were byte-identical despite the composables actually differing. `LoginScreenRenderTest` overrides `@Config(qualifiers = "w360dp-h900dp")` so the whole form (including the debug-only button) is visible in the capture. Any render test for a screen taller than the default viewport needs the same override, or its snapshot silently proves nothing about content past the fold.
