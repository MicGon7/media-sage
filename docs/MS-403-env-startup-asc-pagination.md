# MS-403 — env_startup_ms always null: ascending Cloud Logging query needs a timestamp lower bound

## Symptom

After MS-399 shipped and the orchestrator was redeployed, `jobs.env_startup_ms` was `null`
for every completed worker job. No `Environment startup: …ms` info line and no `[env-startup]`
warning appeared in the orchestrator logs — it was failing silently.

## Root cause

`CloudLoggingClient.fetchFirstLogTimestamp` finds the worker container's first log line by
querying Cloud Logging `entries:list` with `orderBy: "timestamp asc"` and **pageSize 1**, then
taking `entries.firstOrNull()`. The query had **no timestamp lower bound**.

An ascending `entries:list` with no time restriction scans the project's logs oldest-first
across the entire retention window. In that mode the API routinely returns an **empty first
page plus a `nextPageToken`** — meaning "nothing in this scan slice yet, keep paging." The
orchestrator reads only that single page, sees no entries, and returns `null`.

`fetchMetrics` was unaffected: it queries `orderBy: "timestamp desc"`, whose first page starts
at "now" and is immediately populated with the worker's recent output.

### How it was verified

Reproduced both directions against a real execution (`media-sage-agent-worker-2r8xp`, MS-402):

```
gcloud logging read 'labels."run.googleapis.com/execution_name"="media-sage-agent-worker-2r8xp"' --order=asc  --limit=1   # → "Generating GitHub App installation token..." @ 20:35:18
gcloud logging read 'labels."run.googleapis.com/execution_name"="media-sage-agent-worker-2r8xp"' --order=desc --limit=1   # → result event @ 20:37:49
```

`gcloud` returns the ascending entry only because it **auto-paginates** (follows the
`nextPageToken`). The orchestrator's single-page REST call does not, so it got the empty page.

## Fix

Bound the ascending query with `timestamp >= <dispatch time>`. `startedAt` (the job's dispatch
timestamp from `markRunning`) is already passed into `computeEnvStartupMs`, so it is threaded
into `fetchFirstLogTimestamp(executionName, since)` and added to the filter. Capping the scan to
the job's lifetime puts the worker's first log line on the first page.

Also added a `log.warn` on the empty-result path so an unexpected empty query can never fail
silently again.

## Lesson

**Cloud Logging `entries:list` with `orderBy: "timestamp asc"` must include a `timestamp >=`
lower bound.** Without one, the unbounded oldest-first scan can return an empty first page with a
continuation token. Either constrain the time window (preferred when you know the lower bound) or
follow `nextPageToken`. Descending queries don't hit this because they start at the present.

## Verification status

- Unit-verified: the request filter now contains the `timestamp >=` bound and `timestamp asc`
  ordering (`CloudLoggingClientTest`).
- Post-deploy: `env_startup_ms` being non-null on a real run is confirmed by a `pipeline-test`
  smoke ticket after the orchestrator redeploys (see the PR's post-deploy verification section).
