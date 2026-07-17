# MS-570: Carry job type on the completion event so Slack notifications identify each job

> **Note (MS-576).** This doc originally described the `reviewCommentCount` `/tmp`-file handoff by
> analogy to the `/tmp/failed_gate.txt` handoff. That gate handoff was retired in MS-576 (run death
> is not a gate failure; the hardened pipeline suppresses gate failures by design, so `failed_gate`
> was never populated — see `docs/MS-386-jobs-failure-attribution-model.md`). The analogies below
> have been rephrased; `reviewCommentCount` is unchanged.

## Problem

Two jobs can complete for a single ticket — the `ticket-work` run and the downstream
`pr-quality-work` review — and both rendered an identical `MS-XXX — success` line in Slack. The
notification shows the display key (`jiraTicketKey ?: ticketKey`), and quality jobs set
`jiraTicketKey` to the real Jira key, so the two completions were indistinguishable. There was also
no at-a-glance signal of whether a review job's PR link was worth opening.

## What was built

Three thin additions across the pipeline layers, all backward-compatible:

1. **Worker publishes its job type.** `entrypoint-common.sh` → `publish_completion()` adds
   `jobType` to the Pub/Sub payload, read verbatim from the `$JOB_TYPE` env var the worker already
   has. No string-parsing of the dedup-key prefix.
2. **PR link for review-of-existing-PR jobs.** `pr-quality-work` reviews a PR rather than opening
   one, so it never writes `/tmp/worker_pr_url.txt`. `publish_completion()` now falls back to the
   dispatched `$PR_NUMBER` env var when no PR URL file is present, so the link renders for it too.
3. **Programmatic review signal.** The `pr-quality-work` skill counts the comments in the review
   payload it just posted (`/tmp/review.json`) and writes the number to
   `/tmp/review_comment_count.txt` — a purpose-named `/tmp`-file handoff to the entrypoint.
   `publish_completion()` reads it into `reviewCommentCount`. The notifier renders `clean` for 0,
   `N comments` otherwise. **No LLM summary** — it is a count of output the job already produced.

`JobCompletionEvent` gains `jobType: String? = null` and `reviewCommentCount: Int? = null`, both
nullable to stay compatible with older workers and the recovery path (mirroring the existing
nullable-metrics fields). `JobCompletionNotifier` names the job type in the header
(`✅ *MS-314* — pr-quality-work — success`) and adds the review line only for review-type jobs.

## Key design decisions

### Publish the job type; do not derive it from the dedup key
The job family was only inferable by string-parsing the synthetic dedup-key prefix
(`QUALITY-`/`PR-`/`CONFLICT-`). That key exists for **deduplication, not identification** —
overloading it would couple the notifier to an unrelated concern. The worker already knows its
`JOB_TYPE`, so it publishes it as a first-class field. The display-key logic
(`jiraTicketKey ?: ticketKey`) is unchanged: the real Jira key still groups related jobs, and
`jobType` disambiguates them.

### The review signal is a count, not a summary
The full findings already live on the PR. Slack only needs a triage hint — is the link worth
opening? A programmatic comment count answers that for free; an LLM summary would add a billable
pass to restate what the PR already shows. `clean` (0) vs `N comments` is the entire signal.

### Count is written where the review is actually posted
The ticket named `scripts/worker-quality.sh` as the writer, but that script runs the tests+detekt
gates — it never posts a review. The review is posted by the `pr-quality-work` **skill** via
`gh api .../reviews --input /tmp/review.json`, so the count is written there, immediately after the
post, by counting that same payload file. This keeps `entrypoint-common.sh` decoupled from the
skill's internal filename (it reads only the purpose-named `/tmp/review_comment_count.txt`).

`pr-review-work` responds to a review by pushing a fix or posting a single explanation rather than
emitting a review-comment array, so it writes no count file; its `reviewCommentCount` stays null and
the notifier gracefully omits the review line.

## Backward compatibility

Every new field is nullable and absent-safe. An event from an older worker (or the startup recovery
path) parses fine, the header renders without a job type, and no review line appears — satisfying the
AC that a completion without a job type or review signal still produces a valid message and never
disrupts job processing.

## Files touched

- `pipelineCore/.../JobCompletionEvent.kt` — `jobType`, `reviewCommentCount` fields
- `agentruntime/entrypoint-common.sh` — `publish_completion()` payload additions + `PR_NUMBER` fallback
- `agentruntime/.../service/JobCompletionNotifier.kt` — job-type header + review signal
- `.claude/commands/pr-quality-work.md` — write the review-comment count after posting
- `agentruntime/.../JobCompletionNotifierTest.kt`, `JobCompletionEventTest.kt` — coverage
