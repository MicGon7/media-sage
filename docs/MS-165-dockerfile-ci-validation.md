# MS-165: Dockerfile CI Validation

## What changed

Added `.github/workflows/dockerfile-ci.yml` — a dedicated GitHub Actions workflow that runs whenever `agent/Dockerfile` changes. It lints the Dockerfile with Hadolint and builds the image, then probes for required runtime binaries.

## Why a separate workflow file

GitHub Actions `paths` filtering works at the workflow level, not the job level. Putting Dockerfile validation in a separate file means:
- It only runs when `agent/Dockerfile` changes — no wasted CI time on unrelated PRs
- It runs in parallel with the main CI workflow rather than extending its critical path
- The concern is isolated and easy to find

## Why this matters at scale

New engineers adding a dependency to the Dockerfile may introduce a missing binary that causes the agent to fail silently or loop in production. The deployment feedback loop is slow (Railway redeploy + agent run), making these issues expensive to catch late. This workflow surfaces the problem at PR review time instead.

## The `which` pattern

```yaml
docker run --rm --entrypoint which media-sage-agent-test <binary>
```

`--entrypoint which` overrides the container's default ENTRYPOINT (which clones the repo and starts the Ktor server) so the probe runs immediately without side effects. If the binary is present, it prints the path and exits 0. If absent, it exits non-zero and fails the CI step.

Add new binaries to the smoke test list as the container's requirements grow:

```yaml
- name: Smoke test runtime dependencies
  run: |
    docker run --rm --entrypoint which media-sage-agent-test javac
    docker run --rm --entrypoint which media-sage-agent-test java
    docker run --rm --entrypoint which media-sage-agent-test git
```

## Why ubuntu-latest

Docker is native on `ubuntu-latest` GitHub Actions runners — no setup required. The main CI workflow uses `macos-latest` for Android/iOS toolchain compatibility. Dockerfile validation has no such requirement and benefits from the faster, cheaper Linux runner.

## Hadolint

Hadolint is a Dockerfile linter that enforces best practices (e.g., pinned base image versions, avoiding `apt-get` without `--no-install-recommends`, combining `RUN` layers). It runs in seconds without a build and catches common mistakes before they reach the image build step.
