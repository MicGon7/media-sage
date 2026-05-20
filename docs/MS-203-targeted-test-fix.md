# MS-203 — Fix run-affected-tests.sh: Uncommitted Change Detection + Targeted Tests

## What was broken

`run-affected-tests.sh` only diffed committed changes against `main`
(`git diff origin/main...HEAD`). Workers run the script **before committing**,
so it always saw nothing and exited with "No changes detected." Workers then fell
back to running `./gradlew :agent:test --no-daemon` manually — the full module,
every time, regardless of what changed.

## What changed

### 1. Diff now includes committed + staged + unstaged changes

```bash
COMMITTED=$(git diff --name-only origin/main...HEAD ...)
STAGED=$(git diff --name-only --cached ...)
UNSTAGED=$(git diff --name-only HEAD ...)
CHANGED=$(printf '%s\n%s\n%s' "$COMMITTED" "$STAGED" "$UNSTAGED" | sort -u | grep -v '^$')
```

This makes the script work correctly at any point in the workflow — before or
after committing.

### 2. Kotlin gate added

Gradle only runs if `.kt` files in `src/main/` changed. SQL migrations, docs,
config, and shell scripts skip Gradle entirely and delegate to CI.

### 3. Decision table (all 4 cases verified locally)

| Scenario | Script output |
|---|---|
| No changes at all | "No changes detected — delegating to CI" |
| Non-Kotlin change (SQL, docs, .sh) | "No Kotlin source files changed — delegating to CI" |
| Kotlin change with matching `FooTest.kt` | Runs `--tests "pkg.FooTest" --no-daemon` |
| Kotlin change, no matching test class | "No test class mapping found — delegating to CI" |

### 4. CLAUDE.md updated

Removed the manual fallback instruction. The rule is now simple:
> If the script skips for any reason, do not run any Gradle command — CI handles it.

## Why targeted tests are worth keeping

`--tests "X"` filters execution (not compilation). Combined with the Gradle
cache (MS-204), warm compilation drops to ~30s and targeted execution saves
2-3 min vs the full suite. The `FooTest.kt` naming convention is standard
Kotlin/Java — reliable in practice. CI remains the regression safety net.

## Key learning

Workers run tests before committing. Any script that relies solely on
`git diff origin/main...HEAD` will always see nothing in a worker context.
Always combine committed + staged + unstaged diffs for pre-commit tooling.
