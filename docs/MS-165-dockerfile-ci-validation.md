# MS-165: Dockerfile CI Validation

## What changed

Added `.github/workflows/dockerfile-ci.yml` — a dedicated GitHub Actions workflow that runs whenever `agent/Dockerfile` changes. It lints the Dockerfile with Hadolint and builds the image, then probes for required runtime binaries.

Also added `.hadolint.yaml` to suppress the one rule that is impractical to enforce (DL3008 — apt package version pinning).

The first real run of the workflow (against MS-164) surfaced four legitimate issues in the existing Dockerfile that were fixed as part of this work.

## Why a separate workflow file

GitHub Actions `paths` filtering works at the workflow level, not the job level. Putting Dockerfile validation in a separate file means:
- It only runs when `agent/Dockerfile` changes — no wasted CI time on unrelated PRs
- It runs in parallel with the main CI workflow rather than extending its critical path
- The concern is isolated and easy to find

## Why this matters at scale

New engineers adding a dependency to the Dockerfile may introduce a missing binary that causes the agent to fail silently or loop in production. The deployment feedback loop is slow (Railway redeploy + agent run), making these issues expensive to catch late. This workflow surfaces the problem at PR review time instead.

## Hadolint findings on first run

When the workflow first ran against MS-164's Dockerfile, Hadolint reported four issues:

| Rule | Severity | Issue | Fix |
|---|---|---|---|
| DL4006 | Warning | `curl \| bash` and `curl \| dd` pipes without `set -o pipefail` — a silent curl failure looks like success | Added `SHELL ["/bin/bash", "-o", "pipefail", "-c"]` |
| DL3013 | Warning | `pip3 install mcp-atlassian` unpinned — future builds may pull a breaking version | Pinned to `mcp-atlassian==0.21.1` |
| DL3016 | Warning | `npm install -g @anthropic-ai/claude-code` unpinned | Pinned to `@anthropic-ai/claude-code@2.1.139` |
| DL3059 | Info | Multiple consecutive `RUN` instructions | Consolidated `pip3` and `npm` into the main `apt-get` `RUN` layer |

### DL3008 — intentionally ignored

Hadolint also flagged `apt-get install` packages for missing version pins (DL3008). This rule is suppressed in `.hadolint.yaml` because apt package versions are tied to the Ubuntu release and change with each rebuild of the base image — pinning them creates maintenance overhead without meaningful reproducibility benefit. `pip` and `npm` packages are pinned instead, where version drift is more impactful.

## The `which` pattern

```bash
docker run --rm --entrypoint which <image> <binary>
```

`--entrypoint which` overrides the container's default ENTRYPOINT (which clones the repo and starts the Ktor server) so the probe runs immediately without side effects. If the binary is present, it prints the path and exits 0. If absent, it exits non-zero and fails the CI step.

This is a general pattern — use it for any binary the container depends on (`javac`, `git`, `node`, `gh`, etc.).

## Why ubuntu-latest

Docker is native on `ubuntu-latest` GitHub Actions runners — no setup required. The main CI workflow uses `macos-latest` for Android/iOS toolchain compatibility. Dockerfile validation has no such requirement and benefits from the faster, cheaper Linux runner.
