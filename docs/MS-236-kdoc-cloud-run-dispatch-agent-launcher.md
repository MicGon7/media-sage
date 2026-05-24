# MS-236: Add KDoc to CloudRunDispatch and AgentLauncher

## What changed

Added KDoc comments to two files in the `:agent` module:

**`CloudRunDispatch.kt`** — added property-level KDoc for `dispatcher` (executes Cloud Run Jobs
and polls LROs) and `jobs` (persists job state in Supabase Postgres). The class-level KDoc
explaining its role as an optional bundle passed into `AgentLaunchService` was already present.

**`AgentLauncher.kt`** — added interface-level KDoc and per-method KDoc for all four methods:
- `launch` — dispatches an autonomous agent for a Jira ticket, deduplicates by ticket key
- `launchForPrReview` — responds to a formal changes-requested review, pushes a fix commit and re-requests review
- `launchForCommentReview` — answers comment-only reviews by posting a reply without pushing code
- `postInlineCommentReply` — nudges the reviewer to submit a formal changes-requested review so feedback can be batched

## Smoke test purpose

This PR doubles as the MS-234 smoke test. Leaving a `changes_requested` review on the PR will
trigger the GitHub webhook, which should dispatch a Cloud Run Job (not a local process) via the
`launchForPrReview` path. Confirmed by inspecting Cloud Run execution logs after the review.

## Quality gates

- `./gradlew :agent:detekt` — passes
- `./scripts/run-affected-tests.sh` — no test class mapping for pure KDoc changes; CI is authoritative
