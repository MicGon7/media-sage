# MS-396 — Rename `:agent` → `:orchestrator` (PR 1 of 2)

## What & why

MS-396 aligns module names to their work-stream family and deployed identity.
This is the first of two PRs (split by deploy platform for a smaller, independently
verifiable blast radius):

- **PR 1 (this doc): `:agent` → `:orchestrator`** — deploys to GCP Cloud Run.
- PR 2: `:server` → `:appServer` — deploys to Railway.

`:orchestrator` matches its deployed identity (`media-sage-orchestrator`), mirroring
the `:analyst` ↔ `media-sage-analyst` pairing established in MS-394.

Sequenced *before* MS-395 (`:pipelineServer` extraction) so the later extraction targets
the final module names and references aren't touched twice.

## What changed

- **Directory:** `agent/` → `orchestrator/` (whole tree via `git mv`, preserving history).
- **Package:** `com.mediasage.agent` → `com.mediasage.orchestrator` across all sources
  (`orchestrator/` + the `:pipelineScenarios` consumer).
- **Settings:** `settings-agent.gradle.kts` → `settings-orchestrator.gradle.kts`; `include(":agent")`
  → `include(":orchestrator")` in both the full and slim settings.
- **Build/deploy wiring:** `orchestrator/Dockerfile` (gradle task, jar path, `--settings-file`),
  `Dockerfile.worker` + `Dockerfile.lite` (COPY paths), and the `deploy-orchestrator`,
  `build-worker-image`, `build-lite-image`, and `dockerfile-ci` workflows (path filters + Dockerfile refs).
- **Tooling/docs:** `scripts/run-affected-tests.sh`, `pipelineScenarios/build.gradle.kts`,
  root + module `CLAUDE.md`, and `docs/diagrams/infrastructure-overview.md`.

## What deliberately did *not* change

The token `agent` appears in three unrelated namespaces that must survive the rename:

1. **`media-sage-agent`** — the GCP project + Artifact Registry repo name. Renaming the
   module must not touch the registry path (`.../media-sage-agent/media-sage-agent/orchestrator:latest`).
2. **The `agent` Linux user** inside the containers (`useradd agent`, `--chown=agent:agent`,
   `/home/agent`). That's a runtime OS identity, not the Gradle module.
3. **Class/function names** (`AgentLaunchService`, `agentModule`, `AgentDatabase`, …). The
   ticket scopes the *package* rename only; renaming types would be churn with no taxonomy payoff.

This is why a blanket find-replace of "agent" is wrong here. Safe global patterns were limited
to `com.mediasage.agent` (package) and `:agent` (the Gradle accessor — never matches
`media-sage-agent` because there's no colon). Everything else was edited surgically.

## Gotcha: the `agent/` directory was doing double duty

`agent/` held both the orchestrator Gradle module (`build.gradle.kts`, `src/`, `Dockerfile`)
**and** the worker-container shell scripts (`entrypoint-common.sh`, `worker-entrypoint.sh`,
`lite-entrypoint.sh`, `get-github-token.py`) that `Dockerfile.worker` and `Dockerfile.lite`
COPY from. Renaming the directory dragged those scripts to `orchestrator/`, so both worker
Dockerfiles' COPY paths had to move too — even though those scripts are conceptually about the
*worker*, not the orchestrator. Re-homing them is out of scope; this PR just keeps the paths valid.

## Slim-settings sync (the MS-390 lesson, re-applied)

The Cloud Run image builds with `--settings-file settings-orchestrator.gradle.kts`, a slim
settings file that includes only `:orchestrator` + `:pipelineCore` (skipping Android/iOS modules).
Renaming the module means renaming that file *and* its `include(...)` line in lockstep — a miss
here breaks the production image build (the same class of failure as MS-390).

## Verification (all green, local)

| Check | Command | Result |
|---|---|---|
| Module resolves, `:agent` gone | `./gradlew projects` | `:orchestrator` present |
| Compile + unit tests | `./gradlew :orchestrator:test` | BUILD SUCCESSFUL |
| Downstream consumer compiles | `./gradlew :pipelineScenarios:compileTestKotlin` | OK |
| **Slim Docker build path** | `./gradlew :orchestrator:shadowJar --settings-file settings-orchestrator.gradle.kts` | produces `orchestrator-all.jar` |
| Quality gate | `./gradlew detekt` | BUILD SUCCESSFUL |

The slim-shadowJar check is the important one: it exercises the exact build the Cloud Run image
runs, confirming the renamed settings file resolves and the jar name the Dockerfile COPYs
(`orchestrator-all.jar`) is produced.

## Post-deploy verification

Production redeploy (Cloud Run) is the real proof for a rename that touches the deploy pipeline;
it can't be confirmed from a local build. Tracked in the PR's Post-deploy verification section.
