# MS-294: Remove Orchestrator Repo Clone

## What was built

Removed the media-sage repo clone from the orchestrator container. The orchestrator (`media-sage-orchestrator`) previously cloned the repo at startup to support two `gh` CLI calls in `AgentLaunchService`. Both calls were removed outright rather than replaced with GitHub API equivalents.

## Why the clone existed

The orchestrator originally ran Claude Code directly — it needed a local repo for `claude` to work in. When the architecture evolved to dispatch workers (Cloud Run Jobs), the direct Claude Code usage moved to the worker, but the repo clone stayed. It became fossil code propping up two `gh` CLI operations:

1. `gh pr diff` — fetched the PR diff to enrich `BriefingContext.PrReview` before worker dispatch
2. `gh pr comment` — posted a nudge comment on inline PR comments asking reviewers to submit a formal review instead

## Why both operations were removed rather than replaced

**`fetchPrDiff`:** The diff fed `BriefingContext.PrReview`, which the briefing service used to orient the worker before dispatch. But the PR review bootstrap prompt already contains the comment body, branch ref, PR number, and ticket key — everything the worker needs. The briefing for PR reviews added little value, and the diff was what made the briefing prompt coherent at all. Removing the diff made the briefing moot; removing the briefing made the diff moot.

**`postInlineCommentReply`:** The nudge was an orchestrator routing decision — "I can't act on inline comments, please use formal reviews." In practice, reviewers who leave inline comments almost always submit a formal review summary anyway. The guard was protecting against a rare edge case and adding complexity (GitHub API auth in the orchestrator) for minimal benefit.

**The briefing service still runs for ticket work** — that's where it earns its keep. A Jira webhook payload has minimal context; the briefing injects ticket description and AC directly, saving the worker a discovery turn. For PR reviews, the payload is already the context.

## What changed

- `AgentLaunchService`: removed `repoPath` constructor param, `fetchPrDiff`, and `postInlineCommentReply`
- `AgentLauncher`: removed `postInlineCommentReply` from the interface
- `BriefingContext`: removed `PrReview` subclass
- `HttpBriefingService`: removed `prReviewPrompt` and the `PrReview` branch
- `AgentConfig`: removed `repoPath` field
- `AgentModule` / `Application.kt` / `application.conf`: removed `repoPath` wiring
- `agent/entrypoint.sh`: stripped to a single `exec java -jar app.jar` line
- `agent/Dockerfile`: removed git, python3, gh CLI, nodejs, claude-code, mcp-atlassian, and `get-github-token.py` — all installed for the old orchestrator-as-worker pattern
- `GitHubWebhookRoutes`: removed `pull_request_review_comment` handler, `parseInlineCommentPrNumber`, and `GitHubComment` DTO

## Key architectural principle confirmed

The orchestrator's intelligence (briefing service + Claude API) is applied selectively — where it closes a real context gap (ticket work), not uniformly. An orchestrator that skips intelligence when the payload is already sufficient is a better design than one that applies it everywhere.

## Post-deploy

Measure orchestrator cold start time before and after deploying the slimmer image to confirm the improvement.
