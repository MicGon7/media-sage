# MS-53: Deploy Ktor Server to Railway

## What We Did

Deployed the Media Sage Ktor backend to Railway, a cloud hosting platform, making the server permanently available without requiring a laptop to be running. The app on a physical device can now hit a live backend 24/7.

## Why This Matters

Prior to this ticket, the server only ran locally via `./gradlew :server:run`. The mobile app connected to it either over LAN or through an ngrok tunnel. Both required the developer's laptop to be on and running. This deployment eliminates that dependency — the server is now a real cloud service.

## What is Railway?

Railway is a cloud platform-as-a-service (PaaS) that deploys containerized apps directly from a GitHub repository. It handles:

- **Building** — detecting the project type and compiling the code
- **Containerization** — packaging the app into a Docker image via Nixpacks
- **Deployment** — running the container and exposing it via HTTPS
- **Auto-redeploy** — watching the configured branch and redeploying on every push

## What is a Docker Image?

A Docker image is a self-contained snapshot of your app and everything it needs to run (JVM, JAR, config). A container is that image actually running. This is why Railway doesn't need the Android SDK — the image only contains what we explicitly build, which is just the `:server` module.

## railway.toml

Railway reads `railway.toml` before executing anything. Without it, Railpack auto-detected a Gradle project and ran `./gradlew clean build`, which tried to build all modules including `:composeApp` — which requires the Android SDK that Railway doesn't have.

```toml
[build]
builder = "nixpacks"
buildCommand = "./gradlew :server:shadowJar"

[deploy]
startCommand = "java $JAVA_OPTS -jar server/build/libs/server-all.jar"
healthcheckPath = "/health"
healthcheckTimeout = 60
restartPolicyType = "on_failure"
```

Key decisions:
- **`:server:shadowJar`** instead of `:server:build` — `build` produces a plain JAR with no `Main-Class` manifest entry. `shadowJar` produces `server-all.jar`, a fat JAR that bundles all dependencies and sets the manifest correctly so `java -jar` works.
- **`healthcheckPath = "/health"`** — Railway hits this endpoint after deployment to confirm the server is up before routing traffic to it.

## Environment Variables

API keys are set in Railway's **Variables** tab at the service level:

- `CLAUDE_API_KEY`
- `NEWS_API_KEY`
- `SCRIPTURE_API_KEY`

**Important:** After adding or changing variables in Railway, you must click **Apply Changes** — this saves the variables AND triggers a redeploy automatically. Simply clicking "Redeploy" without applying changes first will redeploy with the old (empty) variable values.

Variables that are **not** set on Railway (they only make sense on a local machine with the repo checked out):
- `AGENT_REPO_PATH`
- `GITHUB_WEBHOOK_SECRET`
- `GITHUB_BOT_LOGIN`
- `JIRA_EMAIL`
- `JIRA_API_TOKEN`

The autonomous agent workflow still runs on the developer's laptop — Railway only serves the mobile app's API.

## Auto-Redeploy on Merge

Railway watches the `main` branch. Every time a PR merges, Railway automatically:
1. Pulls the new code
2. Runs `./gradlew :server:shadowJar`
3. Builds a new Docker image
4. Deploys it with a zero-downtime swap (if the health check passes)

This means the server is always in sync with `main` — no manual deploys needed.

## Free Tier Note

Railway's free tier may sleep the container after a period of inactivity. The first request after sleep takes ~5-10 seconds while the container wakes up. Paid tier keeps it always warm.

## Production URL

```
https://media-sage-production.up.railway.app
```

Health check: `GET /health` → `OK`

## What We Learned

- `railway.toml` is essential for multi-module Gradle projects — without it Railway tries to build everything
- `shadowJar` is required for a deployable Ktor server — plain `build` produces a JAR without a Main-Class manifest
- Railway's raw editor adds quotes for display but may include them literally — always use the UI form or CLI to set variables, and always **Apply Changes** before redeploying
- The autonomous agent workflow is fundamentally tied to a local machine (repo + Claude Code CLI) and should not be deployed to Railway
- Docker/containers explain why Railway doesn't need every tool your local machine has — the image only contains what you explicitly build
