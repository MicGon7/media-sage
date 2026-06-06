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

Compose UI tests require `compose.uiTest` in `composeApp/build.gradle.kts`:

```kotlin
commonTest.dependencies {
    implementation(compose.uiTest)
    // existing deps...
}
```

Check whether this dependency is already present before adding it. If it is missing, add it now.

### 5. Place test files in commonTest

```
composeApp/src/commonTest/kotlin/com/mediasage/feature/{name}/{Name}ScreenTest.kt
```

UI tests live in `commonTest` — not `androidTest`. The Compose Multiplatform test API works
across platforms without any Android-specific JUnit4 runner.

### 6. Use runComposeUiTest — the multiplatform API

Google's Compose testing API (`androidx.compose.ui.test`) is accessed via the KMP-compatible
`runComposeUiTest {}` block. Do **not** use `createComposeRule()` (JUnit4-only) or
`@RunWith(AndroidJUnit4::class)` in commonTest.

```kotlin
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.performClick
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class)
class HeadlinesScreenTest {

    @Test
    fun showsLoadingIndicatorWhenStateIsLoading() = runComposeUiTest {
        setContent {
            HeadlinesScreen(
                state = HeadlinesContract.UiState.Loading,
                onIntent = {},
                onNavigateToDetail = {}
            )
        }

        onNodeWithContentDescription("Loading").assertIsDisplayed()
    }
}
```

### 7. Test each UiState variant

Every sealed UiState case (Loading, Success, Error) should have at least one test. Construct the
state directly — no fakes, no repositories:

```kotlin
@Test
fun showsHeadlinesWhenStateIsSuccess() = runComposeUiTest {
    val headlines = listOf(Headline(1L, "Breaking News", "Reuters", "https://x.com", null, 0L, 0L))
    setContent {
        HeadlinesScreen(
            state = HeadlinesContract.UiState.Success(headlines),
            onIntent = {},
            onNavigateToDetail = {}
        )
    }

    onNodeWithText("Breaking News").assertIsDisplayed()
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
fun clickingHeadlineFiresSelectIntent() = runComposeUiTest {
    val headline = Headline(1L, "Breaking News", "Reuters", "https://x.com", null, 0L, 0L)
    val firedIntents = mutableListOf<HeadlinesContract.Intent>()
    setContent {
        HeadlinesScreen(
            state = HeadlinesContract.UiState.Success(listOf(headline)),
            onIntent = { firedIntents.add(it) },
            onNavigateToDetail = {}
        )
    }

    onNodeWithText("Breaking News").performClick()

    assertEquals(1, firedIntents.size)
    assertEquals(HeadlinesContract.Intent.SelectHeadline(headline), firedIntents.first())
}
```

### 10. String resources

Screens use string resources (`stringResource(Res.string.foo)`). In commonTest, resolve them with
`getString(Res.string.foo)` to get the expected value for assertions — do not hardcode the English
string.

### 11. Run the tests

```bash
./scripts/run-affected-tests.sh
```

### 12. Fix detekt violations

```bash
./gradlew detekt
```

### 13. Commit

```
MS-{TICKET}: Add UI tests for {ScreenName}
```

---

The UI test principles that govern this work (no Espresso, no `@RunWith`, no ViewModel or Koin in
test setup, no hardcoded strings) are standing rules in CLAUDE.md — see the "UI test principles"
section under Testing Conventions.
