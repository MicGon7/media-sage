Create and execute a pipeline smoke test ticket that bumps a version counter in SmokeTest.kt.

The variant to bump (a, b, or c) should be provided as an argument, e.g. `/pipeline-test a`. If no variant is given, default to `a`.

## Steps

1. **Read the current version**
   Read `agent/src/main/kotlin/com/mediasage/agent/smoketest/SmokeTest.kt` and find the line:
   ```
   // smoke-test-version-{variant}: N
   ```
   Note the current value of N. The new value will be N+1.

2. **Create the Jira ticket**
   Use the Atlassian MCP to create a Task in project MS (cloudId: `media-sage.atlassian.net`) with:
   - Summary: `Increment smoke-test-version-{variant} in SmokeTest.kt by 1`
   - Label: `pipeline-test`
   - Parent (epic): `MS-4`
   - Assignee: bot account ID `712020:a3ca2e0d-e09f-4117-bf9b-dcbd095be454`
   - Description (use this exact structure — no "why" section, no hardcoded values):

   ```
   ## Task

   Increment the `smoke-test-version-{variant}` counter in `SmokeTest.kt` by 1.

   ## Acceptance Criteria

   - [ ] `smoke-test-version-{variant}` in `SmokeTest.kt` is incremented by 1

   ## Relevant Files

   - `agent/src/main/kotlin/com/mediasage/agent/smoketest/SmokeTest.kt` — the only file that needs to change; bump the version-{variant} line
   ```

3. **Transition to In Progress**
   Call `getTransitionsForJiraIssue` first — never assume an ID. Then transition the new ticket to In Progress.

4. **Create the branch**
   ```bash
   git fetch origin
   git checkout -b feature/MS-{KEY}-bump-smoke-test-version-{variant} origin/main
   ```

5. **Bump the version**
   In `agent/src/main/kotlin/com/mediasage/agent/smoketest/SmokeTest.kt`, change:
   ```
   // smoke-test-version-{variant}: N
   ```
   to:
   ```
   // smoke-test-version-{variant}: N+1
   ```
   Only change the one line for the specified variant. Leave all other version lines untouched.

6. **Commit and push**
   ```bash
   git add agent/src/main/kotlin/com/mediasage/agent/smoketest/SmokeTest.kt
   git commit -m "MS-{KEY}: Bump smoke-test-version-{variant} from {N} to {N+1}"
   git push -u origin feature/MS-{KEY}-bump-smoke-test-version-{variant}
   ```

7. **Open a PR**
   ```bash
   gh pr create \
     --title "MS-{KEY}: Bump smoke-test-version-{variant} from {N} to {N+1}" \
     --body "Pipeline smoke test — increments smoke-test-version-{variant} to verify the end-to-end autonomous pipeline flow."
   ```

