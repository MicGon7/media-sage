# MS-16: CI/CD Pipeline Setup

**Epic:** MS-4 (Infrastructure)
**Date completed:** 2026-04-17

## What was built

- **`.github/workflows/ci.yml`** — GitHub Actions CI pipeline that runs on every push to main and PR targeting main
- **`.github/workflows/jira-transition.yml`** — Auto-transitions Jira tickets to Done when PRs are merged
- **KSP deprecation fix** in `shared/build.gradle.kts`

### CI Pipeline Details

- **Runner:** `macos-latest` (required for iOS framework compilation via Kotlin/Native)
- **JDK:** 21 via `actions/setup-java@v5` (temurin distribution)
- **Gradle caching:** `gradle/actions/setup-gradle@v5` with `cache-read-only` on non-main branches (prevents cache pollution from feature branches)
- **Concurrency groups:** Cancels stale CI runs when new commits are pushed to the same branch
- **Steps:** Build shared → Build server → Build Android → Run allTests

### Jira Auto-Transition

- Triggers on PR merge to main
- Extracts ticket key from branch name (e.g. `feature/MS-16-foo` → `MS-16`)
- Calls Jira REST API to transition ticket to Done (transition ID 31)
- Requires `JIRA_USER_EMAIL` and `JIRA_API_TOKEN` GitHub secrets

## Key decisions & why

- **`macos-latest` runner**: Linux runners are cheaper/faster but can't compile iOS frameworks. Since this is a KMP project with iOS targets, macOS is required. This adds ~5 min to build time compared to Linux.
- **Single job, not parallel**: All builds run sequentially in one job. Simpler to maintain and Gradle's incremental compilation means later steps benefit from earlier compilation. Can split into parallel jobs later if build time becomes a pain point.
- **Actions v5 (Node.js 24)**: v4 actions use Node.js 20 which is deprecated June 2026. Upgrading now avoids a forced migration later.
- **Gradle caching with `cache-read-only` on branches**: Only main writes to the cache. Feature branches read from it. Prevents cache thrashing from parallel PRs writing conflicting cache entries.
- **Auto-transition via REST API**: Simpler than Jira's built-in GitHub integration. Uses branch naming convention we already have. One less third-party integration to manage.

## Concepts learned

- **GitHub Actions workflow syntax**: `on` triggers, `jobs`, `steps`, `uses` for actions, `run` for shell commands, `${{ }}` expressions for context variables.
- **Concurrency groups**: `concurrency.group` with `cancel-in-progress: true` prevents wasted CI minutes on superseded commits.
- **Gradle wrapper JAR**: The `gradlew` script needs `gradle/wrapper/gradle-wrapper.jar` to bootstrap Gradle. CI can't download Gradle without it. It must be committed even if `*.jar` is in `.gitignore` (use `git add -f`).
- **Configuration cache**: Gradle stores the task graph after first build. "no cached configuration" message on first run is normal — subsequent runs skip the configuration phase.
- **KSP target-specific configurations in KMP**: Generic `ksp()` is deprecated. Use `add("kspAndroid", ...)` syntax because KMP doesn't generate function-style DSL accessors for KSP configurations.

## Gotchas

- **Gradle wrapper JAR missing**: First CI run failed because `*.jar` in `.gitignore` excluded the wrapper. Fix: `git add -f gradle/wrapper/gradle-wrapper.jar`.
- **Node.js 20 deprecation warnings**: Actions v4 triggered warnings about Node.js 20 EOL. Fixed by bumping all actions to v5.
- **SSH doesn't work in Claude Code sessions**: The SSH agent doesn't persist between shell processes. Switched git remote to HTTPS and authenticated via `gh` CLI instead.
- **`workflow` scope needed**: Pushing GitHub Actions workflow files requires the `workflow` OAuth scope. Added via `gh auth refresh -s workflow`.
- **Build time**: First CI run (cold cache) took ~14-15 minutes. Expect 5-10 minutes with warm cache.
