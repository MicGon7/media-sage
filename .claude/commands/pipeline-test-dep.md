Create a 4-ticket dependency chain smoke test to verify the MS-521 dispatch-on-unblock feature.

**Prerequisites:** MS-521 merged and new AgentRuntime deployed to production. Do not run this
skill until both are confirmed.

## Dependency chain

```
smoketest-A  (no blocker — dispatched manually to start the chain)
smoketest-B  (blocked by A)
smoketest-C  (blocked by B)
smoketest-D  (blocked by B)
```

## Steps

### 1. Read current versions

Read `agentruntime/src/main/kotlin/com/mediasage/agentruntime/smoketest/SmokeTest.kt`
and find the current values for `smoke-test-version-a`, `smoke-test-version-b`,
and `smoke-test-version-c`. The new values for the 4 tickets will be:
- smoketest-A → bumps `smoke-test-version-a` by 1
- smoketest-B → bumps `smoke-test-version-b` by 1
- smoketest-C → bumps `smoke-test-version-c` by 1
- smoketest-D → bumps `smoke-test-version-a` by 1 (second bump, same counter)

Note the N+1 values before creating the tickets.

### 2. Create the 4 tickets

Use the Atlassian MCP (cloudId: `ad358528-f7e9-4e40-9531-c51049908d6d`) to create 4 Tasks
in project MS. All 4 tickets must have:
- Label: `pipeline-test`
- Parent (epic): `MS-4`
- Assignee: bot account ID `712020:a3ca2e0d-e09f-4117-bf9b-dcbd095be454`

Create them in order A → B → C → D and record each ticket key.

**Ticket A — smoketest-A** (starts the chain):
```
Summary: Increment smoke-test-version-a in SmokeTest.kt (dep-chain A)

## Task
Increment the `smoke-test-version-a` counter in `SmokeTest.kt` by 1.

## Acceptance Criteria
- [ ] `smoke-test-version-a` in `SmokeTest.kt` is incremented by 1

## Relevant Files
- `agentruntime/src/main/kotlin/com/mediasage/agentruntime/smoketest/SmokeTest.kt`
```

**Ticket B — smoketest-B** (blocked by A):
```
Summary: Increment smoke-test-version-b in SmokeTest.kt (dep-chain B)

## Task
Increment the `smoke-test-version-b` counter in `SmokeTest.kt` by 1.

## Acceptance Criteria
- [ ] `smoke-test-version-b` in `SmokeTest.kt` is incremented by 1

## Relevant Files
- `agentruntime/src/main/kotlin/com/mediasage/agentruntime/smoketest/SmokeTest.kt`
```

**Ticket C — smoketest-C** (blocked by B):
```
Summary: Increment smoke-test-version-c in SmokeTest.kt (dep-chain C)

## Task
Increment the `smoke-test-version-c` counter in `SmokeTest.kt` by 1.

## Acceptance Criteria
- [ ] `smoke-test-version-c` in `SmokeTest.kt` is incremented by 1

## Relevant Files
- `agentruntime/src/main/kotlin/com/mediasage/agentruntime/smoketest/SmokeTest.kt`
```

**Ticket D — smoketest-D** (blocked by B):
```
Summary: Increment smoke-test-version-a in SmokeTest.kt (dep-chain D)

## Task
Increment the `smoke-test-version-a` counter in `SmokeTest.kt` by 1 (second increment in chain).

## Acceptance Criteria
- [ ] `smoke-test-version-a` in `SmokeTest.kt` is incremented by 1

## Relevant Files
- `agentruntime/src/main/kotlin/com/mediasage/agentruntime/smoketest/SmokeTest.kt`
```

### 3. Create Jira Blocks links

Call `createIssueLink` to set the dependency links:
- A **blocks** B: inwardIssue=A, outwardIssue=B, type="Blocks"
- B **blocks** C: inwardIssue=B, outwardIssue=C, type="Blocks"
- B **blocks** D: inwardIssue=B, outwardIssue=D, type="Blocks"

Verify: use `getIssueLinkTypes` first if uncertain of the exact type name.

### 4. Start the chain

Call `getTransitionsForJiraIssue` on ticket A, then transition it to **In Progress**.

This fires the Jira webhook → orchestrator → Cloud Run Job for A. The automated cascade
takes over from here when each PR merges.

### 5. Report to the human

Post a comment on each of the 4 tickets with its role in the chain. Then write
`/tmp/jira_comment.txt` on MS-523 in this format:

```
Dependency chain created and started.

Tickets:
- [A key]: smoketest-A — In Progress (worker dispatched)
- [B key]: smoketest-B — blocked by [A key], awaiting auto-dispatch
- [C key]: smoketest-C — blocked by [B key], awaiting auto-dispatch
- [D key]: smoketest-D — blocked by [B key], awaiting auto-dispatch

Human verification steps:
1. When [A key]'s PR merges — confirm [B key] dispatches automatically (no manual In Progress)
2. When [B key]'s PR merges — confirm [C key] and [D key] both dispatch automatically and concurrently
3. Confirm Jira comment "🤖 Dispatched automatically after MS-NNN was merged." on B, C, and D
```
