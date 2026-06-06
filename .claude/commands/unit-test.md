# /unit-test — Write unit tests for the shared or composeApp module

Follow Google's testing guidance: unit tests should be fast, run on the JVM, and test a single unit in
isolation. Aim for the bottom of the testing pyramid — most tests should be unit tests.

## When this skill is called from /ticket-work

The branch is already checked out. Skip straight to step 3 (read canonical example). After writing
and verifying tests, return to /ticket-work — do not open a PR or write /tmp/jira_comment.txt here.

## When this skill is called standalone

1. Transition the Jira ticket to In Progress if it is not already.
2. Check out the existing feature branch: `git checkout feature/MS-{TICKET}-description`
3. Continue from step 3 below, then complete the full workflow (commit, PR, Jira comment, In Review).

---

## Steps

### 3. Read the canonical example first

Read `shared/src/commonTest/kotlin/com/mediasage/data/repository/EncouragementRepositoryTest.kt`
end-to-end before writing a single line of test code. The pattern established there is the
authoritative template for this codebase.

Also read `composeApp/src/commonTest/kotlin/com/mediasage/feature/headlines/HeadlinesViewModelTest.kt`
if the ticket targets a ViewModel.

### 4. Identify the unit under test and its collaborators

- **Repository tests** — collaborators are DAOs and the API interface. Replace both with Fakes.
- **ViewModel tests** — collaborator is the repository interface. Replace with a Fake.
- **Mapper / pure function tests** — no collaborators. Call directly.

Decide the source set:
- `shared/src/commonTest/` for shared module units (repositories, mappers, domain logic)
- `composeApp/src/commonTest/` for ViewModel or composeApp-only logic

### 5. Write Fake collaborators, not mocks

Google recommends test doubles that are lightweight in-memory implementations of the interface,
not mock frameworks. This project uses no mocking library (no Mockito, no MockK).

Fake rules:
- Implement the full interface (let the compiler enforce completeness)
- Use `MutableMap` or `MutableStateFlow` as the in-memory store
- Expose call-count properties (`var insertCallCount = 0`) only when the test needs to assert on them
- Keep Fakes private to the test file (`private class FakeXxx`)
- Fakes belong at the bottom of the test file, below all test cases

### 6. Write tests following AAA structure

Each test:
- Has a name that reads as a sentence describing the expected behaviour (`returnsCachedWhenHit`)
- Is annotated with `@Test` from `kotlin.test`
- Uses `runTest` from `kotlinx-coroutines-test` for any suspending code
- Uses assertions from `kotlin.test` (`assertEquals`, `assertIs`, `assertTrue`, `assertNotNull`, `assertFalse`)
- Tests exactly one behaviour — split multiple assertions into separate tests when they test different paths

ViewModel test setup:
- Declare `private val testDispatcher = UnconfinedTestDispatcher()`
- Use `@BeforeTest` / `@AfterTest` (from `kotlin.test`) to call `Dispatchers.setMain(testDispatcher)` / `Dispatchers.resetMain()`
- Pass `testDispatcher` to `runTest(testDispatcher) { ... }`
- Add `@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)` at the top

### 7. Cover the paths called out in the AC

Write one test per AC behaviour. Name tests to match the AC item so the connection is obvious
to a reviewer.

Common patterns to cover:
- Happy path (data present, operation succeeds)
- Cache-hit vs cache-miss
- Boundary conditions (empty list, null input)
- State transitions (Loading → Success, Loading → Error)
- Side-effect assertions (DAO insert called N times, API not called when cache hit)

### 8. Run the tests

```bash
./scripts/run-affected-tests.sh
```

Never run bare `./gradlew :module:test` directly. If the script skips, CI is the gate — do not retry manually.

### 9. Fix detekt violations

```bash
./gradlew detekt
```

### 10. Commit

```
MS-{TICKET}: Add unit tests for {ClassName}
```

---

## Hard rules

- No mocking libraries. Fakes only.
- No `@RunWith` annotations — this is `kotlin.test`, not JUnit4.
- No business logic in Fakes — they store and return; they do not compute.
- Do not add production code to make tests pass unless the ticket explicitly requires it. If a gap
  in coverage reveals a missing production behaviour, note it in the PR description.
- All test files go in `commonTest`, not `androidTest` or `iosTest` — tests must run on all platforms.
