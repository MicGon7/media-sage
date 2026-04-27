# MS-69: Level 3 Autonomous Agent — Jira Webhook + ngrok

## What We Built

A Level 3 autonomous agent pipeline that eliminates the last manual step in the Level 2 flow. Instead of a human firing the bootstrap command, a Jira webhook triggers it automatically the moment a ticket labeled `autonomous` enters the **To Do** column.

The full flow:

```
Human describes intent to assisted agent
    → Assisted agent creates Jira ticket with autonomous label
    → Jira fires webhook immediately
    → Ktor server receives POST /webhook/jira
    → AgentLaunchService spawns Claude Code process
    → Agent executes full workflow (Jira → branch → code → tests → docs → PR → In Review)
    → Human reviews the PR
```

## How It Works on Laptop (Demo Setup)

### Components

- **Ktor server** (`:server` module) — already running for the media API, now also handles `POST /webhook/jira`
- **ngrok** — tunnels a public HTTPS URL to the local Ktor server port (8080)
- **Jira webhook** — configured in the Atlassian admin to POST to the ngrok URL on issue events

### Step 1: Add AGENT_REPO_PATH to your environment

```bash
# Add to ~/.zshrc
export AGENT_REPO_PATH="/Users/michaelgonzalez/Dev/Learn/Agentic/media-sage"
```

### Step 2: Start the Ktor server

```bash
source ~/.zshrc && ./gradlew :server:run
```

The server starts on port 8080. The webhook endpoint is available at `http://localhost:8080/webhook/jira`.

### Step 3: Start ngrok

```bash
# Install ngrok if not already installed
brew install ngrok

# Expose port 8080
ngrok http 8080
```

ngrok prints a public HTTPS URL such as `https://abc123.ngrok-free.app`. Copy this URL.

### Step 4: Register the Jira webhook

1. Go to **media-sage.atlassian.net → Settings → System → WebHooks**
2. Click **Create a WebHook**
3. Set the URL to: `https://<your-ngrok-url>/webhook/jira`
4. Under **Issue**, check **created** and **updated**
5. Add a JQL filter to limit events: `project = MS AND labels = autonomous`
6. Save

### Step 5: Trigger the demo

Ask the assisted agent to create an `autonomous` ticket:

> "Create an autonomous ticket to rename the Match screen to Headline Detail."

The assisted agent creates the ticket with the `autonomous` label and `To Do` status. Within seconds, Jira fires the webhook, the Ktor server receives it, and a Claude Code process starts autonomously executing the full workflow.

## Webhook Behavior

The webhook receiver at `POST /webhook/jira`:

- Accepts `jira:issue_created` and `jira:issue_updated` events
- Fires the agent only when: `labels` contains `autonomous` **AND** `status.name` is `"To Do"`
- Always returns `200 OK` (so Jira does not retry on agent failures)
- Guards against double-firing: a second webhook for a ticket already in-flight is a no-op
- Logs agent launch, exit code, and any launch failures to server stdout

## Enterprise Architecture

The laptop demo uses ngrok and spawns a local process. In a production enterprise environment, each of these pieces would be replaced with a production-grade equivalent.

### Webhook Receiver

**Laptop:** ngrok + local Ktor server  
**Enterprise:** A dedicated microservice deployed in the cloud (AWS Lambda, Google Cloud Run, Azure Functions). It handles only webhook ingestion and enqueues work — it does not execute the agent directly.

### Agent Execution

**Laptop:** `ProcessBuilder` spawning a `claude` process on the developer's machine  
**Enterprise:** A task queue (SQS, Cloud Tasks, or similar) receives the ticket key. A worker pool pulls from the queue and runs the Claude Code agent in an isolated container (Docker + ECS/GKE). Each agent run has its own ephemeral container with the repo checked out, API keys injected via secrets manager, and a defined compute budget.

### Secrets Management

**Laptop:** Environment variables in `~/.zshrc`  
**Enterprise:** AWS Secrets Manager, GCP Secret Manager, or HashiCorp Vault. Secrets are injected at container startup — never stored in config files or version control.

### Idempotency

**Laptop:** In-memory `ConcurrentHashMap` of active ticket keys (lost on server restart)  
**Enterprise:** A distributed lock (Redis, DynamoDB conditional writes) keyed on ticket ID. Prevents duplicate runs across multiple worker instances and survives restarts. The lock TTL matches the maximum expected agent run time.

### Webhook Security

**Laptop:** No authentication on the webhook endpoint  
**Enterprise:** Jira supports a **webhook secret** (HMAC-SHA256 signature in the `X-Hub-Signature` header). The receiver validates the signature before processing. Invalid or missing signatures return `401` immediately.

### Observability

**Laptop:** `java.util.logging` to stdout  
**Enterprise:** Structured JSON logs shipped to a log aggregation platform (Datadog, Splunk, CloudWatch Logs). Each agent run emits: ticket key, start time, end time, exit code, PR URL. Metrics: runs per hour, success rate, p95 duration. Alerts: agent failure rate > threshold, queue depth growing.

### Failure Handling

**Laptop:** Failed launch is logged, webhook returns 200 (Jira does not retry)  
**Enterprise:**
- Webhook always returns 200 to Jira (prevent retry storms)
- Failed agent runs are written to a dead-letter queue for manual review
- A Slack/Teams notification is sent to the team when an autonomous agent fails
- The Jira ticket is transitioned back to **To Do** with a comment explaining the failure, so a human can re-trigger or convert to `assisted`

### Access Control

**Laptop:** Any ticket labeled `autonomous` triggers the agent  
**Enterprise:** Additional guards:
- Only tickets in approved projects (allowlist by project key)
- Only specific issue types (Task, Story — not Epic or Bug)
- Agent runs under a dedicated service account with scoped Jira/GitHub permissions
- Human approval gate option for high-risk tickets (e.g., those touching database migrations)

### Cost Management

**Laptop:** No constraints  
**Enterprise:** Each agent run has a token budget (Claude API spend limit per run). Runs exceeding the budget are killed and flagged. Monthly spend is tracked per team/project.

## Summary: Laptop vs. Enterprise

| Concern | Laptop Demo | Enterprise |
|---|---|---|
| Webhook exposure | ngrok tunnel | Cloud-hosted microservice |
| Agent execution | Local process | Containerized worker pool |
| Secrets | ~/.zshrc env vars | Secrets Manager + container injection |
| Idempotency | In-memory set | Distributed lock (Redis/DynamoDB) |
| Webhook auth | None | HMAC-SHA256 signature validation |
| Observability | stdout logs | Structured logs, metrics, alerts |
| Failure handling | Log + continue | DLQ + Slack alert + ticket rollback |
| Access control | Label only | Project allowlist + service account + budget |
