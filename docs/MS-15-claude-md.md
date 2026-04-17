# MS-15: CLAUDE.md & Development Conventions

**Epic:** MS-4 (Infrastructure)
**Date completed:** 2026-04-17

## What was built

- `CLAUDE.md` at the repo root — the primary development guide and AI agent instruction file
- `.claude/` added to `.gitignore` to keep local Claude Code config out of version control

## Key decisions & why

- **CLAUDE.md as single source of truth**: Rather than scattering conventions across wikis and READMEs, CLAUDE.md serves as both human documentation and AI agent instructions. It's automatically loaded by Claude Code when working in this repo.
- **`.claude/` excluded from git**: Contains machine-specific settings (permissions, MCP server config) and local memory files. Not useful to other developers or machines.
- **Conventions chosen for KMP context**:
  - `kotlinx.serialization` only (no Gson/Moshi) — works across all KMP targets
  - Ktor Client in shared, Ktor Server in server — never mix (prevents accidental JVM-only dependencies in shared code)
  - Room schemas in `shared/schemas/` — needed for migration testing

## Concepts learned

- **CLAUDE.md pattern**: A convention for AI-assisted development where project context, conventions, and agent instructions live in a markdown file at the repo root. AI tools read it automatically.
- **Agent guidelines section**: Defines how AI agents should discover work (Jira labels), verify acceptance criteria, and submit work (run tests, no secrets in code).
- **Convention-as-documentation**: Good conventions reduce the need for code review comments. If the convention is in CLAUDE.md, the AI follows it automatically.

## Gotchas

- The previous Claude session saved this file but never committed it — always verify work is committed before ending a session.
- Acceptance criteria weren't defined on the ticket before work started. This led to establishing the rule: all tickets must have acceptance criteria before beginning work.
