# MS-213 — Smoke Test Worker Metrics End-to-End

## Purpose

This ticket is a pipeline smoke test. MS-210 added token/cost/efficiency tracking to the
`jobs` table, and MS-155 fixed `CloudLoggingClient` to handle the real `jsonPayload` format
emitted by the Cloud Logging API, including the `modelUsage` fallback when top-level `usage`
tokens are zeroed.

MS-213 exercises the full Cloud Run worker pipeline with a live execution to confirm that
metrics are populated in Supabase after each run.

## What Was Changed

Added `generatedAt: Long` to the daily reflection data flow:

- `DailyReflectionResult` — the service's internal result type, populated with
  `System.currentTimeMillis()` at generation time
- `DailyReflectionResponse` — the server's serialized response type, mapped from the result
- `DailyReflectionResponseDto` — the client-side DTO with a default of `0L` for
  backward compatibility with cached responses

The field propagates from server generation time through the JSON response to the client.
Existing tests (validation-only, 400 paths) were unaffected by this change.

## Metrics Verification (Post-Merge)

After this PR merges and the Cloud Run worker processes the job, the `jobs` row for
ticket `MS-213` should have non-null values for:

| Column | Expected |
|---|---|
| `input_tokens` | > 0 |
| `output_tokens` | > 0 |
| `cache_read_tokens` | > 0 (prompt cache is active) |
| `total_cost_usd` | > 0.0000 |
| `claude_duration_ms` | > 0 |
| `num_turns` | > 0 |

The orchestrator logs should show:
```
[MS-213] Metrics: N turns, Xh Ym Zs, $0.XXXX
```

## Key Learnings

1. **Smoke tests validate infrastructure, not features** — The code change itself is
   intentionally trivial. Its value is confirming that the full metrics pipeline (Cloud Run
   worker → Cloud Logging → orchestrator → Supabase) is wired and producing correct data.

2. **Backward-compatible DTO changes use default values** — Adding `val generatedAt: Long = 0L`
   to the client DTO means existing server responses that lack the field deserialize correctly
   (field defaults to `0L`) without breaking anything.

3. **`System.currentTimeMillis()` as a default parameter** — This pattern stamps the result
   with the generation time automatically. The service doesn't need to be changed at the call
   site — the default fires when the data class is constructed.
