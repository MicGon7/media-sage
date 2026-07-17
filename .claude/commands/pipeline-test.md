Create a pipeline smoke test ticket that triggers the autonomous worker to bump a version counter in SmokeTest.kt.

The variant to bump (a, b, or c) should be provided as an argument, e.g. `/pipeline-test a`. If no variant is given, default to `a`.

## Resolve the smoke test file (do this first)

Every step below references the smoke test file by the symbol `SMOKE_TEST_PATH` — never write its literal path more than once.

1. Start from the expected location: `agentruntime/src/main/kotlin/com/mediasage/agentruntime/smoketest/SmokeTest.kt`.
2. Confirm it exists. If it does not (e.g. the module was renamed), locate it with `Glob **/SmokeTest.kt` and use the path returned.
3. Set `SMOKE_TEST_PATH` to the confirmed path. Use that resolved value everywhere below — in the read, in the description, and in the "Relevant Files" entry. Do not paste the literal from step 1 into the ticket; substitute the resolved `SMOKE_TEST_PATH` so a stale path can never ship into a generated ticket.

## Steps

1. **Read the current version**
   Read `SMOKE_TEST_PATH` and find the line:
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
   - Description (use this exact structure — no "why" section, no hardcoded values; substitute the resolved `SMOKE_TEST_PATH`):

   ```
   ## Task

   Increment the `smoke-test-version-{variant}` counter in `SmokeTest.kt` by 1.

   ## Acceptance Criteria

   - [ ] `smoke-test-version-{variant}` in `SmokeTest.kt` is incremented by 1

   ## Relevant Files

   - `SMOKE_TEST_PATH` — the only file that needs to change; bump the version-{variant} line
   ```

3. **Transition to In Progress**
   Call `getTransitionsForJiraIssue` first — never assume an ID. Then transition the new ticket to In Progress.

   This fires the Jira webhook → orchestrator → Cloud Run worker. The worker handles everything from here (branch, bump, commit, PR). Your job is done.
