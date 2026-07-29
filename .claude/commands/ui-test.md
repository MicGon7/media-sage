# /ui-test — Write Compose UI tests for the composeApp module

Follow Google's testing guidance for Compose: test stateless composables in isolation by passing
state and callbacks directly — no ViewModel, no Koin, no navigation stack. UI tests sit at the top
of the testing pyramid and should cover rendering behaviour and user interactions that unit tests
cannot exercise.

## When this skill is called from /ticket-work

The branch is already checked out. Skip straight to step 3 (read the screen under test). After
writing and verifying tests, return to /ticket-work — do not open a PR or write /tmp/jira_comment.txt here.

## When this skill is called standalone

1. Transition the Jira ticket to In Progress if it is not already.
2. Check out the existing feature branch: `git checkout feature/MS-{TICKET}-description`
3. Continue from step 3 below, then complete the full workflow (commit, PR, Jira comment, In Review).

---

## Steps

### 3. Read the screen under test

Read the `{Name}Screen.kt` and `{Name}Contract.kt` files for the feature being tested. Screens are
stateless composables that receive `state`, `onIntent`, and navigation lambdas — understanding the
full parameter surface is required before writing tests.

Also read `composeApp/CLAUDE.md` for the MVI contract conventions (sealed UiState, Channel side
effects, no ViewModel dependency in Screen).

### 4. Check the dependency

Compose UI interaction tests require `androidx.compose.ui:ui-test-junit4` (catalog alias
`libs.androidx.compose.ui.testJunit4`) in `androidUnitTest.dependencies` in
`composeApp/build.gradle.kts` — **not** `compose.uiTest`/`runComposeUiTest`, which fails at
runtime on this project's Android unit-test target (see step 6). Check whether the dependency is
already present before adding it. If it is missing, add it now:

```kotlin
androidUnitTest.dependencies {
    // existing roborazzi/robolectric deps...
    implementation(libs.androidx.compose.ui.testJunit4)
}
```

Also confirm `composeApp/src/debug/AndroidManifest.xml` declares a launcher
`androidx.activity.ComponentActivity` entry (see step 6) — `createComposeRule()` launches that
activity via `ActivityScenario`, and Robolectric can't resolve it against the app's own
`.MainActivity`-only manifest. Add the file if it's missing:

```xml
<manifest xmlns:android="http://schemas.android.com/apk/res/android">
    <application>
        <activity android:name="androidx.activity.ComponentActivity" android:exported="true">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>
    </application>
</manifest>
```

### 5. Place test files in androidUnitTest

```
composeApp/src/androidUnitTest/kotlin/com/mediasage/feature/{name}/{Name}ScreenTest.kt
```

Not `commonTest` — see step 6 for why.

### 6. Use createComposeRule() + AndroidJUnit4 — not runComposeUiTest

`runComposeUiTest {}` (the KMP-common API) needs Robolectric's shadow environment active on the
Android unit-test target (for `android.os.Build.*`), which only activates under a JUnit4
`@RunWith`. A `commonTest` class has no runner to carry that annotation, so it compiles but fails
every test at runtime with `NullPointerException: ... Build.FINGERPRINT is null` — a known,
unresolved Compose Multiplatform/Robolectric gap (robolectric/robolectric#10727), not something
fixable from this project's side. See `docs/MS-686-compose-ui-test-robolectric.md`.

Use the JUnit4 `createComposeRule()` API instead, with the same Robolectric setup as the
`*RenderTest` files:

```kotlin
import android.app.Application
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = Application::class)
class HeadlinesScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun showsLoadingIndicatorWhenStateIsLoading() {
        composeTestRule.setContent {
            HeadlinesScreen(
                state = HeadlinesContract.UiState.Loading,
                onIntent = {},
                onNavigateToDetail = {}
            )
        }

        composeTestRule.onNodeWithContentDescription("Loading").assertIsDisplayed()
    }
}
```

### 7. Test each UiState variant

Every sealed UiState case (Loading, Success, Error) should have at least one test. Construct the
state directly — no fakes, no repositories:

```kotlin
@Test
fun showsHeadlinesWhenStateIsSuccess() {
    val headlines = listOf(Headline(1L, "Breaking News", "Reuters", "https://x.com", null, 0L, 0L))
    composeTestRule.setContent {
        HeadlinesScreen(
            state = HeadlinesContract.UiState.Success(headlines),
            onIntent = {},
            onNavigateToDetail = {}
        )
    }

    composeTestRule.onNodeWithText("Breaking News").assertIsDisplayed()
}
```

### 8. Use semantic finders — not layout position

Google's guidance: test from the user's perspective using content semantics, not implementation
details like component type or position.

Preferred finders:
- `onNodeWithText("exact string")` — for visible text
- `onNodeWithContentDescription("description")` — for icons and images
- `onNodeWithTag("testTag")` — for components with no natural semantics (add `Modifier.testTag("x")` to production code when needed)

Avoid:
- `onAllNodes(isRoot())` — fragile, position-dependent
- Assertions on exact pixel coordinates

### 9. Test interactions

Verify that user actions fire the correct intent. Capture fired intents in a list:

```kotlin
@Test
fun clickingHeadlineFiresSelectIntent() {
    val headline = Headline(1L, "Breaking News", "Reuters", "https://x.com", null, 0L, 0L)
    val firedIntents = mutableListOf<HeadlinesContract.Intent>()
    composeTestRule.setContent {
        HeadlinesScreen(
            state = HeadlinesContract.UiState.Success(listOf(headline)),
            onIntent = { firedIntents.add(it) },
            onNavigateToDetail = {}
        )
    }

    composeTestRule.onNodeWithText("Breaking News").performClick()

    assertEquals(1, firedIntents.size)
    assertEquals(HeadlinesContract.Intent.SelectHeadline(headline), firedIntents.first())
}
```

### 10. String resources

Screens use string resources (`stringResource(Res.string.foo)`). Resolve them with
`runBlocking { getString(Res.string.foo) }` to get the expected value for assertions — do not
hardcode the English string.

### 11. Run the tests

```bash
./scripts/run-affected-tests.sh
```

> **Render tests are not written here.** This skill writes `createComposeRule()` interaction/state tests only. The `captureRoboImage` render test that produces PR screenshots is authored once, up front, in `/ticket-work` step 5 — before its single `capture-ui.sh` build. Do not add `captureRoboImage` blocks in this skill.

### 12. Fix detekt violations

```bash
./gradlew detekt
```

### 13. Commit

```
MS-{TICKET}: Add UI tests for {ScreenName}
```

---

The UI test principles that govern this work (no Espresso, no ViewModel or Koin in test setup, no
hardcoded strings, `androidUnitTest` + `createComposeRule()` for interaction tests) are standing
rules in CLAUDE.md — see the "UI test principles" section under Testing Conventions.
