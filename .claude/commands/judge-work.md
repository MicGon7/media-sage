## Judgment vs mechanical audit (pattern: `docs/tool-consolidation-pattern.md`)

| Step | Type | Notes |
|---|---|---|
| `judge-fetch.sh` | Mechanical | Single script: PR metadata + Jira AC + diff signals |
| Evaluate all verdict dimensions | Judgment | AC compliance, test coverage, regression surface, PR accuracy |
| Post PR comment + write jira_comment.txt | Mechanical | Parallel tool calls — no dependency on each other |

**Never emit a text-only turn.** Each turn must contain at least one tool call. Do not announce what you are about to do — proceed directly to action.

---

**Hard requirement:** If `$PR_NUMBER` is unset or empty, exit immediately — do NOT fall back to `gh pr list`:
```bash
[[ -z "${PR_NUMBER:-}" ]] && { echo "ERROR: PR_NUMBER is required" >&2; exit 1; }
```
The orchestrator always passes `PR_NUMBER` at dispatch. A missing value means a dispatch bug, not a reason to spend a recovery turn.

---

**Turn 1 — fetch all inputs (one Bash call):**
```bash
./scripts/judge-fetch.sh "$PR_NUMBER"
```

This prints to stdout: PR title, branch, body, Jira AC, diff signals (test files present, shared infra files, all changed files), and full diff. Read everything from this result — no `cat` or `Read` calls needed before the verdict turn.

---

**Turn 2 — evaluate and deliver verdict:**

Evaluate all dimensions from the fetch output. Then issue both tool calls in parallel in this same turn:

**a) Post a PR review comment** (NOT an approval or request-for-changes):
```
gh pr review <pr-number> --comment --body "$(cat <<'EOF'
🤖 **Agent:** Judge verdict for {JIRA_KEY}

**AC Compliance:**
✅/❌/⚠️ {ac item} — {one-line explanation of what in the diff satisfies or violates it}

**Test coverage:** ✅/❌/⚠️ {did the diff include tests for any new behavior introduced?}

**Regression surface:** ✅ No shared infra changed / ⚠️ {filename} — {explain what this file is shared with and why it warrants review}

**PR description accuracy:** ✅ Accurate / ❌/⚠️ {what is missing or misleading vs the actual diff}

**Overall:** PASS / FAIL / PARTIAL

This verdict is informational. The human reviewer makes the final call.
EOF
)"
```

Verdict rules per dimension:
- **AC item**: ✅ Met (with diff evidence) | ❌ Not met (must name what is missing) | ⚠️ Partial (what is present, what is missing). Skip items that are CI gates (Detekt passes, tests pass, PR targets main) — those are not diff-verifiable.
- **Test coverage**: ✅ if `judge-fetch` reported test files in the diff covering new behavior | ❌ if implementation added with no test changes | ⚠️ if test files changed but scope is unclear from diff alone
- **Regression surface**: Pull directly from the fetch output's "Shared infra files" list. ✅ if empty. ⚠️ if any shared infra files changed — name each file and explain the shared usage.
- **PR description accuracy**: ✅ if the PR body describes what the diff actually does | ❌ if the body claims changes not present in the diff | ⚠️ if description is vague or omits significant changes

**b) Write `/tmp/jira_comment.txt`** (plain text only — no `**bold**` or markdown):
```
🤖 Agent: Judge verdict for {JIRA_KEY}

Task: Judge verdict on PR #{PR_NUMBER}

AC compliance:
✅/❌/⚠️ {ac item} — {verdict}

Test coverage: ✅/❌/⚠️ {result}
Regression surface: ✅/⚠️ {result}
PR description: ✅/❌/⚠️ {result}

Overall: PASS / FAIL / PARTIAL
```

Do NOT post via curl or Jira API — the entrypoint appends metrics and posts directly after you exit.

**Do NOT** approve the PR, request changes, or merge. Comment only — the human reviewer acts on the verdict.
