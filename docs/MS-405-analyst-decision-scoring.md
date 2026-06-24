# MS-405: Analyst — Decision Scoring Against a Rubric

## What was built

The Analyst now scores completed worker sessions against a rubric using Claude as a judge. This
moves the System Intelligence layer from outcome-only reporting (pass rate, cost, gate failures) to
decision-quality evaluation — giving MS-389's auto-PR detector richer signal to cite in rationale.

## How it works

When a Pub/Sub completion event arrives and a matching job row is found, the Analyst fires
`ClaudeDecisionScorer.score(jobId)` in the application scope (after responding 200 — never
blocking delivery). The scorer:

1. Reads the worker session transcript from the `transcripts` table (MS-387).
2. Loads `analyst/src/main/resources/rubrics/decision-scoring.md` from the classpath — a
   versioned Markdown file defining three criteria.
3. Calls Claude (`claude-sonnet-4-6`) as a judge with the transcript and rubric, using
   `output_config.format` with a JSON schema to guarantee valid structured output without parsing.
4. Deserialises the response directly and persists one row per criterion to `decision_scores`.

`GET /stats` now surfaces low-scoring patterns (criteria averaging below 3.5) alongside existing
pass/cost/time metrics. When no scores exist yet for the window, `lowScorePatterns` is null rather
than an empty list — callers can tell the difference between "no data" and "no problems."

## The rubric

Three criteria, each scored 1–5:

- **tool_choice** — did the agent use the right tool for each task?
- **retry_recovery** — did the agent diagnose and recover from errors on the first attempt, or loop?
- **context_management** — did the agent avoid redundant fetches and act on current state?

The rubric lives at `analyst/src/main/resources/rubrics/decision-scoring.md`. Evolving the
criteria only requires a PR to that file — no code change needed.

## What was learned

### Claude-as-judge fires after 200

The Pub/Sub handler must return 200 quickly — GCP will retry on any non-2xx. Scoring is kicked
off with `call.application.launch { decisionScorer.score(...) }` which runs in the application's
coroutine scope, outliving the request coroutine. A `NoOpDecisionScorer` stands in when
`ANTHROPIC_API_KEY` is not set so no code path fails on missing config.

### `output_config.format` eliminates JSON parsing risk

The scorer passes `output_config: {format: {type: "json_schema", ...}}` in the API request. Claude
returns raw JSON matching the schema — no markdown fences, no extra keys, no parsing gamble.
The `extractJson()` fallback and the explicit JSON-format instruction in the prompt are gone; the
contract is enforced at the API layer instead.

### `decision_index = 0` for session-level scores

The schema has `(job_id, decision_index, criterion)` as primary key to support per-turn
granularity later. First implementation uses `decision_index = 0` for one overall session score
per criterion. Future iterations can score individual tool-use turns without a schema change.

### Low-score threshold is 3.5

The stats query surfaces criteria averaging below 3.5 (mid-range on a 1–5 scale). This is
conservative enough to avoid noise on first run while still flagging genuine patterns. The
threshold is in the SQL query and can be adjusted without a rubric change.

### Rubric is a resource file, not code

Hardcoding rubric text in a Kotlin string would require a code change and rebuild to evolve
criteria. Loading from `src/main/resources` means a PR to the `.md` file is enough — reviewable
as a plain-language document, not a Kotlin diff.

## Migration required

Run `analyst/migrations/001_create_decision_scores.sql` in Supabase SQL Editor before deploying
the Analyst with `CLAUDE_API_KEY` set. The Analyst will operate normally without the table if
scoring is disabled, but will fail at runtime if `CLAUDE_API_KEY` is set and the table is absent.

## Environment variables added

| Variable | Module | Purpose |
|---|---|---|
| `ANTHROPIC_AUTH_TOKEN` | `:analyst` | Auth token for ClaudeDecisionScorer. Reuses the existing `anthropic-auth-token` Secret Manager secret — same token used by worker jobs via the Fuelix proxy. Absent = scoring disabled. |
| `ANTHROPIC_BASE_URL` | `:analyst` | Anthropic API base URL. Set to `https://api.fuelix.ai` on Cloud Run. Defaults to `https://api.anthropic.com` when absent, so local dev works without a proxy. |

The analyst service account (`media-sage-analyst@media-sage-agent.iam.gserviceaccount.com`) was
granted `roles/secretmanager.secretAccessor` on the `anthropic-auth-token` secret to enable
mounting it on the Cloud Run service.
