# MS-382: Build the Analyst reactive spine (`:feedback` module)

## What was built

A new `:feedback` Gradle module — a Ktor server (port 8082) that is the **reactive half** of the
System Intelligence epic (MS-380). It is the first consumer of pipeline data that is *not* the
orchestrator. This slice deliberately does only three things:

1. **Subscribes** to the existing `cloud-run-job-completions` Pub/Sub topic on its **own** push
   subscription, so it learns about every worker completion.
2. **Reads** the matching row from the shared `jobs` table (via `:pipeline-core`'s `JobRegistry`)
   to record the outcome — it never writes; the orchestrator owns job state.
3. **Exposes** `GET /stats[?days=N]` — a windowed cross-run health summary over data that already
   exists in `jobs` (recorded since MS-179).

Schema enrichment, transcript migration, the Slack digest, and the auto-PR failure detector are
deliberately *out of scope* — they are follow-ups (MS-386 → MS-389) so this slice ships and
verifies on its own.

## Module structure

```
feedback/
├── build.gradle.kts                 — JVM Ktor server; depends on :pipeline-core
└── src/main/kotlin/com/mediasage/feedback/
    ├── Application.kt                — entry point, port 8082, Koin + routing
    ├── di/AnalystConfig.kt           — supabaseDbUrl + pubSubWebhookSecret only
    ├── di/AnalystModule.kt           — Koin wiring; fail-fast DB connectivity check
    ├── db/FeedbackDatabase.kt        — read connection; NO migrations (orchestrator owns schema)
    ├── plugins/                      — ContentNegotiation, CallLogging, StatusPages (mirror :agent)
    ├── routes/PubSubCompletionRoutes.kt — the reactive read path
    ├── routes/StatsRoutes.kt         — GET /stats[?days=N]
    └── stats/                        — RunStats, PipelineStatsReader, JobsTableStatsReader
```

## Pattern: the reactive monitor (consumer), not a critical-path service

The orchestrator (`:agent`) and the Analyst (`:feedback`) are both Cloud Run Services that receive
HTTP, but they sit in opposite positions relative to the work:

| | Orchestrator | Analyst |
|---|---|---|
| Role | Drives the pipeline (dispatches workers) | Watches the pipeline (records outcomes) |
| Trigger | Jira/GitHub webhooks (**synchronous** caller) | Pub/Sub push + Cloud Scheduler |
| Cold-start cost | A cold start = a **dropped webhook** (Jira does not retry reliably) → stuck ticket | Pub/Sub **retries** on any non-2xx → a cold start just delays processing |
| Correct scaling | `--min-instances=1` (always warm) | `--min-instances=0` (scale to zero) |

This asymmetry is the core lesson: **scaling config follows the failure mode, not the runtime.**
Two services using identical Ktor/Netty machinery have opposite correct configs because one is in
the critical path and one is not. Keeping the Analyst at `min-instances=0` also costs nothing when
idle — right for a low-volume, cost-conscious project.

The handler reflects this: it does the cheap lookup, then `respond(200)` and returns. There is no
attempt to make Pub/Sub wait — the event bus is the retry mechanism.

## Design decisions

**Reads, never writes.** `FeedbackDatabase` connects and runs `SELECT 1`; it deliberately runs no
`ALTER TABLE` or `CREATE`. Schema ownership stays with the orchestrator. A second writer to the
same table would be a recipe for migration races — the Analyst is a pure reader.

**Its own Pub/Sub subscription.** Pub/Sub delivers a *separate copy* of each message to every
subscription on a topic. The Analyst adds a new push subscription; the orchestrator's existing one
is unaffected. Fan-out is the idiomatic way to add a second consumer — no shared queue, no
competition for messages.

**Windowed `/stats` from day one.** The endpoint takes `?days=N` (default 7) rather than a fixed
window. The daily Slack digest (MS-388) will call `?days=1`; a weekly review uses `?days=7`. Adding
the parameter now avoids reworking the contract later. Invalid `days` returns **400**, not a silent
fallback — a malformed automation request should surface as an error, not as wrong data.

**Aggregation lives in `:feedback`, not `:pipeline-core`.** `JobsTableStatsReader` queries the
shared `JobsTable` definition but returns a `:feedback`-owned `RunStats` (the API response shape).
This keeps `:pipeline-core` a pure, portable schema/repository layer and keeps response modelling
where the endpoint lives.

**`PipelineStatsReader` is an interface.** Routes depend on the capability, not the database, so
tests pass a `FakeStatsReader` and exercise the full HTTP path (param parsing, validation, JSON
serialization) with no Postgres. Same for the Pub/Sub route via a `FakeJobRegistry`.

## The `/stats` query

A single SQL pass uses Postgres `FILTER` clauses to scope each aggregate to the right rows:

- `passRate` = COMPLETED / terminal runs (terminal = COMPLETED + FAILED + INTERRUPTED). PENDING and
  RUNNING are excluded — they have no outcome yet.
- Averages (`avg_cost`, `avg_wall_seconds`, `avg_turns`) are **nullable**: an empty window returns
  `null`, not a misleading `0.0`.
- The window is `now() - make_interval(days => N)`. `N` is interpolated as a validated `Int`, so
  there is no SQL-injection surface (no string input ever reaches the query).

## Deployment runbook

The `deploy-analyst.yml` workflow builds the image and deploys the service on merge to `main`,
referencing a dedicated service account and two secrets. Those must exist **before** the first
deploy, or the container crash-loops on a blank `SUPABASE_DB_URL` and the deploy fails.

Secret naming follows the existing per-service convention (`orchestrator-…`, `pipe-…` →
`analyst-…`). The Analyst reads the *same* Supabase DB as the orchestrator, so its DB-URL secret
reuses that value; its Pub/Sub token is fresh (its own subscription).

```bash
PROJECT=media-sage-agent
REGION=us-central1
SA=media-sage-analyst

# 1. Dedicated least-privilege service account
gcloud iam service-accounts create "$SA" --project="$PROJECT" \
  --display-name="Media Sage Analyst (feedback)"

# 2. Secrets — DB URL copies the orchestrator's value; token is freshly generated
gcloud secrets versions access latest --secret=orchestrator-supabase-db-url --project="$PROJECT" \
  | gcloud secrets create analyst-supabase-db-url --project="$PROJECT" --data-file=-
openssl rand -hex 32 \
  | gcloud secrets create analyst-pubsub-webhook-secret --project="$PROJECT" --data-file=-

# 3. Grant the SA read access to only those two secrets
for S in analyst-supabase-db-url analyst-pubsub-webhook-secret; do
  gcloud secrets add-iam-policy-binding "$S" --project="$PROJECT" \
    --member="serviceAccount:${SA}@${PROJECT}.iam.gserviceaccount.com" \
    --role="roles/secretmanager.secretAccessor"
done

# 4. Merge the PR → deploy-analyst.yml builds the image and deploys the service.

# 5. Point a NEW Pub/Sub push subscription at the Analyst (the orchestrator's is untouched)
TOKEN=$(gcloud secrets versions access latest --secret=analyst-pubsub-webhook-secret --project="$PROJECT")
URL=$(gcloud run services describe media-sage-analyst --project="$PROJECT" --region="$REGION" --format='value(status.url)')
gcloud pubsub subscriptions create cloud-run-job-completions-analyst --project="$PROJECT" \
  --topic=cloud-run-job-completions \
  --push-endpoint="${URL}/webhook/pubsub?token=${TOKEN}" \
  --ack-deadline=60

# 6. Verify end-to-end
curl -s "${URL}/stats?days=7"
```

## What this unblocks

`/stats` is the first real read of the historical `jobs` table. It is also how we will *measure*
whether there is enough failure density to justify the auto-PR detector (MS-389) — the data answers
its own question before we build the machinery to act on it.
