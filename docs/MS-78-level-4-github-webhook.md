# MS-78: Level 4 — GitHub Webhook for Autonomous PR Review Responses

## What Was Built

A `POST /webhook/github` route on the Ktor server that receives GitHub review events and spawns an autonomous Claude Code agent to respond. This closes the Level 3 loop: the human's only touchpoint is leaving a PR review comment. The agent handles the rest.

## How It Works

```
Human leaves PR review comment
  → GitHub fires webhook to POST /webhook/github
    → Server validates HMAC-SHA256 signature
    → Server extracts ticket key from branch name (e.g. feature/MS-42-... → MS-42)
    → Server calls Jira API to check if ticket is labeled `autonomous`
    → If autonomous: spawns agent with PR number + comment as context
    → Agent pushes fix commit or replies with 🤖 **Agent:** prefix
```

## Event Types Handled

| GitHub event | Action | Triggers agent? |
|---|---|---|
| `pull_request_review` | `submitted` with `state = changes_requested` or `commented` | Yes, if autonomous |
| `pull_request_review` | `submitted` with `state = approved` | No — no action needed |
| `pull_request_review_comment` | `created` | Yes, if autonomous |
| Any other event | — | No |

## Loop Guards

The agent can only create an infinite feedback loop if it leaves a review comment that triggers itself. Three guards prevent this:

1. **Bot login guard**: skips events where `sender.login` matches `GITHUB_BOT_LOGIN`
2. **Comment prefix guard**: skips comments that start with `🤖 **Agent:**` (the agent's own prefix)
3. **Double-fire guard**: `AgentLaunchService` de-duplicates by PR number (`PR-{number}`) — only one agent runs per PR at a time

## Security: HMAC-SHA256 Signature Validation

GitHub signs every webhook payload with a shared secret using HMAC-SHA256. The signature is sent in the `X-Hub-Signature-256` header as `sha256=<hex>`.

The server:
1. Reads the raw request bytes **before** JSON parsing
2. Computes `HMAC-SHA256(GITHUB_WEBHOOK_SECRET, rawBody)`
3. Compares with the header value using `MessageDigest.isEqual` (constant-time — prevents timing attacks)
4. Returns 401 if invalid

Never compare HMAC signatures with `==` — timing attacks can reveal the expected value.

## Laptop Setup

### 1. Environment variables

Add to `~/.zshrc`:

```bash
export GITHUB_WEBHOOK_SECRET="<generate with: openssl rand -hex 32>"
export GITHUB_BOT_LOGIN="MicGon7"   # GitHub login that pushes agent commits
export JIRA_EMAIL="micgon7@gmail.com"
export JIRA_API_TOKEN="<from Atlassian account settings → API tokens>"
```

The `JIRA_CLOUD_ID` has a default in `application.conf` (`ad358528-f7e9-4e40-9531-c51049908d6d`) so it only needs to be set if you change Jira instances.

### 2. Start ngrok

```bash
ngrok http 8080
```

Copy the `https://` URL.

### 3. Register GitHub webhook

In the repo: **Settings → Webhooks → Add webhook**

| Field | Value |
|---|---|
| Payload URL | `https://<ngrok-url>/webhook/github` |
| Content type | `application/json` |
| Secret | same value as `GITHUB_WEBHOOK_SECRET` |
| Events | `Pull request reviews`, `Pull request review comments` |

### 4. Start the server

```bash
source ~/.zshrc && ./gradlew :server:run
```

### 5. Test end-to-end

1. Create or find a PR on a branch linked to an `autonomous`-labeled ticket
2. Leave a review comment requesting changes
3. Watch the server logs — agent should launch
4. Agent pushes a fix or replies with `🤖 **Agent:**`

## Enterprise Notes

In a production environment:

- **Fixed webhook URL**: Replace ngrok with a stable URL (load balancer, Railway, Render, etc.)
- **Dedicated bot account**: Create a separate GitHub account for the agent so `GITHUB_BOT_LOGIN` is reliably distinct from human contributors
- **Webhook secret rotation**: Treat `GITHUB_WEBHOOK_SECRET` like any other secret — rotate periodically, store in a secrets manager
- **Rate limiting**: GitHub may fire multiple events per PR action; the double-fire guard handles burst events but consider adding a short debounce for very active PRs
- **Audit log**: The agent's responses are visible in the PR timeline, giving full traceability

## Key Design Decisions

### Why `JiraLabelChecker` interface?
The GitHub webhook route needs to check Jira labels outbound (unlike the Jira webhook which receives labels in the payload). Extracting the interface allows tests to swap in a `FakeJiraLabelChecker` without making real HTTP calls.

### Why `takeIf` chains in `parseWebhookContext`?
Detekt enforces a max of 4 return statements per function. Chaining `takeIf` reduces the filter logic to a single expression — more readable and stays within the rule.

### Why `CoroutineScope` injected into `AgentLaunchService`?
`Process.waitFor()` is a blocking JVM call. The previous `Thread { }` approach worked but wasn't idiomatic for a Ktor coroutine-based server. Injecting the application scope (`this@module` in Application.kt) means process watchers run on `Dispatchers.IO` and their lifecycle is tied to the server — they're cancelled cleanly on shutdown.

### Why de-duplicate by PR number rather than ticket key?
A single PR may receive multiple review comments. Using PR number as the de-dup key ensures only one agent runs per PR at a time (avoids race conditions / conflicting commits), while the ticket-key de-dup in `launch()` continues to apply for Jira-triggered flows.
