Create a UI pipeline smoke test ticket that triggers the autonomous worker to make a small visible change to `SmokeTestScreen.kt`, render it, and attach the screenshot to the PR.

This is the visual analog of `/pipeline-test`: where that bumps a counter in `SmokeTest.kt`, this bumps the on-screen version in `SmokeTestScreen.kt` so the render loop (MS-581) is exercised end to end — the worker changes UI, renders it headlessly, self-critiques, and posts the PNG on the PR.

## Steps

1. **Read the current version**
   Read `composeApp/src/commonMain/kotlin/com/mediasage/feature/smoketest/SmokeTestScreen.kt` and find both:
   ```
   // smoke-test-ui-version: N
   ```
   and the displayed `Text(text = "vN", ...)`. Note the current N. The new value is N+1 in both places.

2. **Create the Jira ticket**
   Use the Atlassian MCP to create a Task in project MS (cloudId: `media-sage.atlassian.net`) with:
   - Summary: `Increment smoke-test-ui-version in SmokeTestScreen.kt by 1`
   - Label: `pipeline-test`
   - Parent (epic): `MS-4`
   - Assignee: bot account ID `712020:a3ca2e0d-e09f-4117-bf9b-dcbd095be454`
   - Description (use this exact structure — no "why" section, no hardcoded values):

   ```
   ## Task

   Increment the `smoke-test-ui-version` in `SmokeTestScreen.kt` by 1 — update both the
   `// smoke-test-ui-version:` marker comment and the displayed `Text(text = "vN")` so the
   change is visible in the rendered screenshot.

   ## Acceptance Criteria

   - [ ] `smoke-test-ui-version` marker and the displayed version text in `SmokeTestScreen.kt` are both incremented by 1
   - [ ] The rendered screenshot showing the new version is attached to the PR

   ## Relevant Files

   - `composeApp/src/commonMain/kotlin/com/mediasage/feature/smoketest/SmokeTestScreen.kt` — the only file that needs to change; bump both the marker and the displayed version
   ```

3. **Transition to In Progress**
   Call `getTransitionsForJiraIssue` first — never assume an ID. Then transition the new ticket to In Progress.

   This fires the Jira webhook → orchestrator → Cloud Run worker. Because the change touches a `composeApp` composable, the worker's UI render step (`scripts/capture-ui.sh`) renders the screen, the worker reviews the PNG, and attaches it to the PR. Your job is done.

## Prerequisite

The worker renders UI only once its container image ships a compile-time Android SDK. Until that image update lands, `capture-ui.sh` skips the render (exit 3, non-fatal) and the worker opens a PR with the version bump but no screenshot — the smoke test still passes, it just doesn't exercise the render. Run this skill against a worker image that includes the SDK to test the full loop.
