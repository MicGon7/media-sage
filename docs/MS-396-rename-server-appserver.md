# MS-396 — Rename `:server` → `:appServer` (PR 2 of 2)

## What & why

Second and final PR of MS-396. Renames the product API module `:server` → `:appServer`,
completing the work-stream taxonomy:

- **Product side:** `:composeApp` (UI) + `:appServer` (its backend) + `:shared`
- **Pipeline side:** `:orchestrator`, `:analyst`, `:pipelineCore` (renamed in MS-394 / PR 1)

`:server` read ambiguously next to the pipeline modules; `:appServer` names it as the *app's*
backend and pairs naturally with `:composeApp`. (PR 1 = `:agent` → `:orchestrator`, GCP/Cloud Run.
This PR = Railway. Split by deploy platform for independent blast radius.)

## What changed

- **Directory:** `server/` → `appServer/` (`git mv`, history preserved).
- **Package:** `com.mediasage.server` → `com.mediasage.appserver` across all sources.
  Note the casing: the **module** is camelCase `:appServer`, the **package** is lowercase
  `appserver` (Kotlin package convention) — matching the ticket's AC exactly.
- **Railway deploy config** (`railway.toml`): `buildCommand` → `:appServer:shadowJar`;
  `startCommand` → `appServer/build/libs/appServer-all.jar`.
- **Build/CI wiring:** `settings.gradle.kts`, `Dockerfile.worker` (COPY + `:appServer:dependencies`),
  `ci.yml`, `build-worker-image.yml`, `run-affected-tests.sh`, the two slim-settings skip-list comments.
- **Docs:** root + module `CLAUDE.md`, `docs/diagrams/infrastructure-overview.md`, `docs/diagrams/app-flow.md`.

## Two gotchas worth remembering

1. **The Ktor entry point is named twice.** Renaming the package wasn't enough — `application.conf`
   hard-codes the fully-qualified module function:
   `modules = [com.mediasage.server.ApplicationKt.module]`. A `.kt`-only find-replace misses it,
   and the server boots fine in a `:appServer:build` (the test harness calls the module function
   directly) but would **fail to start under `EngineMain`** in production, because HOCON resolves
   that string at runtime. Same story for `mainClass` in `build.gradle.kts`. Both updated.

2. **The Railway jar name is coupled to the module name.** The Ktor/Shadow fat jar is named
   `<project>-all.jar`, so renaming the module changes the artifact from `server-all.jar` to
   `appServer-all.jar`. `railway.toml`'s `startCommand` references that path literally — miss it
   and the deploy builds green but crashes on start (`Unable to access jarfile`). Verified locally:
   `:appServer:shadowJar` produces `appServer-all.jar`.

## Incidental fix (called out, not silent)

`appServer/scripts/regen-figure.sh` invoked `./gradlew :server:generateImages`, but `generateImages`
actually lives in `:scripts` (`scripts/build.gradle.kts`) — it was a stale reference, broken since
the MS-136 module split. Deleting `:server` would have turned a wrong-module reference into a
nonexistent-module reference, so it's corrected to `:scripts:generateImages` here rather than left
dangling. (Pre-existing; flagged so it isn't mistaken for part of the rename.)

## What deliberately did *not* change

- The `serverModule(...)` Koin function and other `Server`-named identifiers — package-only rename.
- Ktor library coordinates (`ktor-server-core`, etc.) — unrelated namespace.
- "Ktor server" as a generic term in prose.

## Verification (all green, local)

| Check | Command | Result |
|---|---|---|
| Module resolves, `:server` gone | `./gradlew projects` | `:appServer` present |
| Compile + unit tests + coverage | `./gradlew :appServer:build` | BUILD SUCCESSFUL |
| Railway jar name | `./gradlew :appServer:shadowJar` | produces `appServer-all.jar` |
| Quality gate | `./gradlew detekt` | BUILD SUCCESSFUL |

## Post-deploy verification

Railway redeploy is the real proof (the rename touches `railway.toml`'s build + start commands).
After merge:
- [ ] Railway builds `:appServer:shadowJar` and starts `appServer-all.jar` cleanly
- [ ] `/health` responds and the app fetches headlines/encouragements end-to-end
