# MS-257: Redesign e2e Scenarios to Test the Full Webhook Flow

## What was built

Both `e2eConflictResolution` and `e2ePrReviewResponse` previously called `service.launchFor*()` directly from the test. That approach tested the Cloud Run dispatch and worker in isolation — it bypassed the orchestrator's entire webhook handling stack (signature verification, payload parsing, bot identity check, dedup gate).

This ticket redesigned both scenarios to POST a crafted webhook payload to the live orchestrator URL with a valid HMAC-SHA256 signature. The orchestrator processes it identically to a real GitHub event. Every line of production code in the webhook path now runs end-to-end.

## The simpler approach

The original ticket description proposed generating a GitHub App installation token (JWT → GitHub API exchange) and enrolling PRs in the merge queue via GraphQL to trigger real webhook events. That turned out to be unnecessary — and significantly more complex.

**Key insight:** The orchestrator can't distinguish a test POST from a real GitHub webhook. All that matters is:
1. The HMAC-SHA256 signature is valid (computed from the same secret the orchestrator uses)
2. The payload shape matches what the orchestrator expects
3. The `pull_request.user.login` is the bot login (the MS-258 identity gate)

So the test sets up a real GitHub fixture (branch + conflicting commits + PR) to produce a valid `prNumber` and `branchName`, then fires a crafted payload at the orchestrator. No GitHub App token generation, no GraphQL, no merge queue enrollment.

The infrastructure test still creates a real Cloud Run Job — that part isn't simulated.

## What `postWebhook()` does

```kotlin
protected suspend fun postWebhook(eventType: String, payload: String) {
    val bodyBytes = payload.toByteArray(Charsets.UTF_8)
    val signature = "sha256=${hmacSha256(config.webhookSecret, bodyBytes)}"
    val response = httpClient.post("${config.orchestratorUrl}/webhook/github") {
        header("X-GitHub-Event", eventType)
        header("X-Hub-Signature-256", signature)
        setBody(TextContent(payload, ContentType.Application.Json))
    }
    check(response.status.isSuccess()) { "Webhook POST to orchestrator failed: ${response.status}" }
}
```

Lives on `FullPipelineScenarioBase` — available to any future full pipeline scenario.

## New env vars required

| Var | Purpose |
|---|---|
| `ORCHESTRATOR_URL` | Live orchestrator base URL (Cloud Run Service URL) |
| `GITHUB_WEBHOOK_SECRET` | Shared secret for HMAC signature computation |

Both forwarded in `pipelineScenarios/build.gradle.kts` and read via `ScenarioConfig.fromEnv()`.

## What each scenario covers

Both scenarios confirm these production code paths:

1. **HMAC signature verification** — orchestrator rejects tampered payloads
2. **Payload parsing and bot identity check** — `pull_request.user.login == botLogin` (MS-258)
3. **Ticket key extraction** — branch names use `feature/MS-257-e2e-*` so the `[A-Z]+-\d+` regex extracts `MS-257`
4. **Supabase dedup gate** — real job inserted, dedup checked
5. **Cloud Run Job dispatch** — real worker dispatched

### ConflictResolutionE2eTest

- Creates a real merge conflict on GitHub (same file edited on both feature branch and `e2e-base`)
- Opens a real PR so `prNumber` is valid
- Fires `pull_request` `dequeued` `merge_conflict` event payload to orchestrator
- Polls Supabase for `COMPLETED` (40-minute timeout)

Dedup key: `"CONFLICT-$prNumber"`

### PrReviewResponseE2eTest

- Creates a branch with a trivial scratch commit, opens a real PR
- Fires `pull_request_review` `submitted` `changes_requested` event payload to orchestrator
- Polls Supabase for `COMPLETED` (20-minute timeout)

Dedup key: `"PR-$prNumber"`

## Important limitation discovered

`waitForCompletion()` polls for any terminal state (COMPLETED, FAILED, INTERRUPTED). The test passes as soon as Supabase shows COMPLETED — regardless of whether the worker actually completed the task.

The current implementation marks jobs COMPLETED when the container exits via the `trap EXIT` handler — even if the worker was cancelled or crashed. This means:

- A cancelled or failed worker still marks COMPLETED
- The test has false positives: it validates the pipeline infrastructure (webhook → dispatch → Supabase update), not the quality of the work the worker did

This is tracked in **MS-260** (worker should report actual success/failure via Pub/Sub).

## Also shipped: MS-258 learning doc

The MS-258 PR merged without a learning doc. That doc was written during this work and committed to this PR branch: `docs/MS-258-bot-identity-gate.md`.

## Files changed

| File | Change |
|---|---|
| `pipelineScenarios/src/test/kotlin/.../pipeline/ConflictResolutionE2eTest.kt` | Full redesign — GitHub fixture setup + webhook POST |
| `pipelineScenarios/src/test/kotlin/.../pipeline/PrReviewResponseE2eTest.kt` | Full redesign — GitHub fixture setup + webhook POST |
| `pipelineScenarios/src/test/kotlin/.../support/PipelineScenarioBase.kt` | Added `postWebhook()`, `hmacSha256()`, orchestrator/secret checks |
| `pipelineScenarios/src/test/kotlin/.../support/ScenarioConfig.kt` | Added `orchestratorUrl`, `webhookSecret` |
| `pipelineScenarios/build.gradle.kts` | Forwarded `ORCHESTRATOR_URL`, `GITHUB_WEBHOOK_SECRET` to test JVM |
| `docs/MS-258-bot-identity-gate.md` | Bundled MS-258 learning doc (missed the MS-258 PR window) |
