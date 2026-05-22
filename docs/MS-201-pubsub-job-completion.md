# MS-201: Replace Cloud Run LRO polling with Pub/Sub completion events

## What was built

The Cloud Run job completion pipeline was redesigned from a poll-based model to an event-driven model. The worker now signals the orchestrator when it is done instead of the orchestrator repeatedly asking if the worker is done.

**Before:** `executeJob()` dispatched the job then blocked in a 30-second poll loop for up to 30 minutes per run, tying up a coroutine and adding latency between job completion and metric recording.

**After:** `executeJob()` dispatches and returns immediately. The worker publishes a Pub/Sub message when Claude Code exits. GCP delivers it via HTTP push to `POST /webhook/pubsub`. The orchestrator acknowledges in ~1ms and processes metrics in a background coroutine.

## Architecture

```
orchestrator                    Cloud Run worker
     │                                │
     │── dispatch job ──────────────> │
     │<─ operation name ─────────────-│  (returns immediately)
     │                                │
     │                    claude runs...
     │                                │
     │                    claude exits (success/failure)
     │                                │
     │                    publish to Pub/Sub ──> cloud-run-job-completions topic
     │                                                    │
     │<─────── POST /webhook/pubsub ──────────────────────│
     │   (ticketKey, executionName, status)
     │── respond 200 immediately
     │
     background: fetch metrics from Cloud Logging
                 post Jira comment
                 mark COMPLETED in Supabase
```

## Key design decisions

### Worker owns the completion event (producer-consumer pattern)
The first instinct was to use a Cloud Logging sink — filter on the worker's `result` log line and route it to Pub/Sub. This would have worked but was the wrong design: logs are for observability, not control flow. Coupling the orchestrator to the worker's stdout format and Cloud Logging ingestion timing creates invisible, fragile dependency. Human judgment caught this before any code was written.

The correct pattern: the producer (worker) owns the event. It knows when it's done. It publishes the signal. The orchestrator subscribes. This is the standard producer-consumer model on every cloud platform.

### Acknowledge immediately, process in background
Pub/Sub retries delivery if the subscriber does not respond within the push deadline. Metrics fetch can take ~15 seconds (Cloud Logging ingestion delay + retries). Responding 200 before processing ensures Pub/Sub never misinterprets a slow metrics fetch as a delivery failure.

### Idempotent processing
`findRunningByTicketKey()` looks for a RUNNING job. If the job is already COMPLETED (e.g. a duplicate delivery), the lookup returns null and the handler exits cleanly with a warning log. No double-processing.

### Recovery safety net retained
`recoverInterruptedJobs()` on startup still checks RUNNING jobs. If the orchestrator was down when a job completed and the Pub/Sub message was not delivered, recovery catches it by checking the LRO status once. Pub/Sub subscriptions buffer undelivered messages (7-day default retention), so most missed events are delivered automatically when the orchestrator restarts.

## GCP setup

1. **Pub/Sub topic**: `cloud-run-job-completions` in project `media-sage-agent`
2. **IAM**: `media-sage-orchestrator` SA granted `roles/pubsub.publisher`
3. **Push subscription**: `cloud-run-job-completions-push` → `https://<orchestrator-url>/webhook/pubsub?token=<secret>`
4. **Worker env vars** (baked into Cloud Run job definition, not per-run overrides):
   - `GCP_PROJECT_ID=media-sage-agent`
   - `PUBSUB_TOPIC=cloud-run-job-completions`
5. **Orchestrator env var**: `PUBSUB_WEBHOOK_SECRET=<hex secret>` (Railway + `~/.zshrc`)

## Authentication

The push subscription URL includes `?token=<secret>`. The orchestrator verifies this on every delivery. Requests with a missing or wrong token are rejected with 401. This is the same pattern used for the GitHub webhook secret — simple, effective, no JWT verification overhead.

## Worker entrypoint changes

`exec claude ...` was changed to `claude ...` (without exec) so the shell can capture the exit code after Claude Code finishes. The Pub/Sub publish runs after Claude exits using a token from the GCP metadata server (always available inside Cloud Run). A publish failure is logged as a warning and does not fail the job — `recoverInterruptedJobs()` is the fallback.

## Smoke test result (MS-222)

- Dispatch returned in ~600ms (vs. blocking for full job duration before)
- Pub/Sub event received and acknowledged in 1ms
- Metrics fetched in background: 16 turns, $0.31, 4m 17s
- Bot Jira comment posted ✅
- Supabase row COMPLETED with full metrics ✅

## Files changed

- `agent/worker-entrypoint.sh` — publish completion event after claude exits
- `agent/src/main/kotlin/com/mediasage/agent/routes/PubSubWebhookRoutes.kt` — new route
- `agent/src/main/kotlin/com/mediasage/agent/service/CloudRunJobsClient.kt` — remove poll loop, add `onJobCompleted()`
- `agent/src/main/kotlin/com/mediasage/agent/service/CloudRunDispatch.kt` — expose `client` accessor
- `agent/src/main/kotlin/com/mediasage/agent/service/AgentLaunchService.kt` — expose `cloudRun` internally
- `agent/src/main/kotlin/com/mediasage/agent/db/JobRegistry.kt` — add `findRunningByTicketKey()`
- `agent/src/main/kotlin/com/mediasage/agent/db/JobRepository.kt` — implement `findRunningByTicketKey()`
- `agent/src/main/kotlin/com/mediasage/agent/di/AgentConfig.kt` — add `pubSubWebhookSecret`
- `agent/src/main/resources/application.conf` — add `pubSub.webhookSecret`
- `agent/src/main/kotlin/com/mediasage/agent/Application.kt` — wire Pub/Sub route
