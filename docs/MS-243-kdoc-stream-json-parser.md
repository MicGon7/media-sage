# MS-243: Add KDoc to StreamJsonParser

## What changed

Added KDoc to `StreamJsonParser.kt` in the `:agent` module.

The file contains no class — only top-level functions. The single `internal` function
`parseStreamJsonMilestone` is the public API of this file; the private helpers are
implementation details and do not require KDoc.

The KDoc on `parseStreamJsonMilestone` documents:
- Purpose: parses one newline-delimited JSON line from `claude --output-format stream-json`
  into a concise human-readable milestone string for Jira progress comments
- The four recognised event types (`system`, `assistant`, `user`, `result`)
- The null-return contract for unrecognised or empty lines
- `@param line` and `@return` tags

## Quality gates

- `./gradlew :agent:detekt` — passes
- `./scripts/run-affected-tests.sh` — Gradle daemon crashed mid-execution (environment memory
  constraint); CI is the authoritative quality gate. The change is KDoc-only and cannot affect
  compilation or test outcomes.
