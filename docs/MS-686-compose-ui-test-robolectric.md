# MS-686: Compose UI interaction tests need androidUnitTest + createComposeRule, not commonTest + runComposeUiTest

## What changed

Added empty states for the Reader tab's memory-quote card, past-briefings carousel, and the
History screen's calendar view. Writing the first Compose UI interaction tests in this repo for
those screens surfaced a real gap between this project's documented UI test convention and how
Compose Multiplatform actually behaves on the Android unit-test target.

## The documented convention

`composeApp/CLAUDE.md`'s UI test principles say: no `@RunWith(AndroidJUnit4::class)` in
`commonTest` — use `runComposeUiTest {}` (the Compose Multiplatform API) instead. This works for
plain composable rendering, but `ReaderScreenTest`/`ReaderHistoryScreenTest` (assertions like
`onNodeWithText(...).assertIsDisplayed()`) hit a runtime failure that had never been exercised in
this codebase before, since no prior ticket had written a `commonTest` interaction test:

```
java.lang.NullPointerException: Cannot invoke "String.toLowerCase(java.util.Locale)"
because "android.os.Build.FINGERPRINT" is null
	at androidx.compose.ui.test.RobolectricIdlingStrategy_androidKt.getHasRobolectricFingerprint
```

## Root cause

`runComposeUiTest` on the Android JVM unit-test target needs Robolectric's shadow environment
active to populate `android.os.Build.*`. Robolectric only activates under a JUnit4
`@RunWith(RobolectricTestRunner::class)` (or `AndroidJUnit4`, which delegates to Robolectric on
the JVM). A `commonTest` class compiled for the `androidUnitTest` target has no such runner —
JUnit falls back to a plain default runner, so Robolectric's shadows are never installed and
`Build.FINGERPRINT` stays null. This is a known, currently-unresolved gap between Compose
Multiplatform's KMP test API and Robolectric (robolectric/robolectric#10727) — not something
fixable from this project's side.

## The fix

Compose UI **interaction** tests (ones that resolve string resources, click, or assert on
displayed content) live in `androidUnitTest`, not `commonTest`, using the JUnit4
`createComposeRule()` API — the same Robolectric setup (`@RunWith(AndroidJUnit4::class)`,
`@Config(sdk = [34], application = Application::class)`) already used by the `*RenderTest` files:

```kotlin
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = Application::class)
class ReaderScreenTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun showsQuoteEmptyStateWhenQuoteCardIsMissing() {
        val expectedTitle = runBlocking { getString(Res.string.reader_quote_empty_title) }
        composeTestRule.setContent {
            ReaderScreen(state = ReaderContract.UiState.Ready(quoteCard = null), onIntent = {})
        }
        composeTestRule.onNodeWithText(expectedTitle).assertIsDisplayed()
    }
}
```

Two supporting changes were needed:

1. **Dependency**: `createComposeRule()` lives in `androidx.compose.ui:ui-test-junit4`, a
   pure-Android artifact — not part of Compose Multiplatform's `compose.uiTest` (which only
   resolves to `androidx.compose.ui:ui-test`/`ui-test-android` for the common `runComposeUiTest`
   API). Added `libs.androidx.compose.ui.testJunit4` and declared it in
   `androidUnitTest.dependencies`, not `commonTest.dependencies`.
2. **Manifest**: `createComposeRule()` launches `androidx.activity.ComponentActivity` via
   `ActivityScenario`, which Robolectric couldn't resolve because the app manifest only declares
   `.MainActivity`. Added `composeApp/src/debug/AndroidManifest.xml` declaring
   `androidx.activity.ComponentActivity` with a launcher intent-filter — a debug-only source set,
   never bundled into a release build.

## When to use which

- **Composable rendering only, no assertions on interaction/resolved strings** (e.g. golden-image
  snapshots) → `androidUnitTest` `*RenderTest` + `captureRoboImage` (unchanged, pre-existing
  pattern).
- **Compose UI interaction tests** (assert on displayed text/nodes, resolve string resources,
  simulate clicks) → `androidUnitTest`, `createComposeRule()` + `@RunWith(AndroidJUnit4::class)`,
  matching this ticket's `ReaderScreenTest`/`ReaderHistoryScreenTest`.
- `commonTest` + `runComposeUiTest {}` remains correct for **non-Android** Compose Multiplatform
  targets (e.g. iOS), where no Robolectric shadow is needed — just not as the sole home for a test
  that must also execute on the Android unit-test target under this project's current toolchain.
