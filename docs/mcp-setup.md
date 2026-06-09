# MCP Setup

These MCP servers are pre-configured for this project and available in every assisted session. Use them instead of making raw API calls or running CLI commands manually.

## Project-local (`.claude.json`)

| Server | Transport | Purpose |
|--------|-----------|---------|
| `atlassian` | SSE → `https://mcp.atlassian.com/v1/sse` | Jira (tickets, transitions, comments) + Confluence (impact page updates) |
| `railway` | HTTP → `https://mcp.railway.com` | Deployment history, service logs, restart counts, environment variables |
| `gcloud-mcp` | stdio `npx -y @google-cloud/gcloud-mcp` | Cloud Run job management, GCP resource queries |
| `observability-mcp` | stdio `npx -y @google-cloud/observability-mcp` | Cloud Run execution logs, job duration metrics, cost data |

**Setup commands** (run once per machine from the project root):
```bash
claude mcp add railway --transport http https://mcp.railway.com
claude mcp add gcloud-mcp -- npx -y @google-cloud/gcloud-mcp
claude mcp add observability-mcp -- npx -y @google-cloud/observability-mcp
```

Railway authenticates via OAuth on first use (browser prompt). GCP MCPs inherit the active `gcloud auth` session — run `gcloud auth login` first if needed.

## Account-level (claude.ai MCP marketplace)

These are connected at the account level and available across all projects:

| Server | Purpose |
|--------|---------|
| Atlassian (claude.ai) | Redundant Jira/Confluence access via claude.ai OAuth |
| Mermaid Chart | Generate architecture diagrams |
| Excalidraw | Whiteboard-style diagrams |
| Figma | Design file access (currently failing — known auth issue) |

## Intended uses

- **Jira workflow**: use `atlassian` MCP tools instead of `gh` CLI or manual curl
- **Railway deploys**: use `railway` MCP to check service status and logs after a PR merges
- **Cost reporting**: use `gcloud-mcp` + `observability-mcp` to pull Cloud Run job execution times and build cost documents for the orchestrator/worker pattern
- **Confluence updates**: use `atlassian` MCP to update the Agentic Development Impact page (ID: `163844`)
