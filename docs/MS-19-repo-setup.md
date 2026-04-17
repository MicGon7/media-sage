# MS-19: Initial Repo Setup

**Epic:** MS-4 (Infrastructure)
**Date completed:** 2026-04-15

## What was built

- `.gitignore` covering Kotlin/Gradle, IDE files, OS files, environment/secrets, KMP build outputs, and Xcode artifacts
- GitHub PR template (`.github/pull_request_template.md`) with summary, changes, testing, and checklist sections
- GitHub issue templates for bugs and features (`.github/ISSUE_TEMPLATE/bug.yml`, `feature.yml`)

## Key decisions & why

- **YAML-based issue templates** (not markdown): YAML templates render as structured forms in GitHub, making it easier to capture required fields like reproduction steps or acceptance criteria.
- **Comprehensive .gitignore from day one**: Prevents accidental commits of IDE config, secrets, or build artifacts. Includes KMP-specific paths (`/composeApp/build/`, `/shared/build/`, `/server/build/`).

## Concepts learned

- GitHub issue templates support two formats: markdown (`.md`) and YAML (`.yml`). YAML provides form-based input with dropdowns, checkboxes, and required fields.
- PR templates auto-populate the description when opening a new PR on GitHub.

## Gotchas

- The `.gitignore` included `*.jar` which later caused the Gradle wrapper JAR to be excluded — fixed in MS-16.
