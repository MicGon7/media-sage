#!/usr/bin/env bash
# PreToolUse guard for the Bash tool: blocks the worker from running a bare
# ./gradlew compile/test task against :composeApp or :shared directly.
#
# Those two modules are intentionally skipped by scripts/run-affected-tests.sh
# (a cold KMP build/test invocation against them can consume most of a Cloud
# Run Job's 30-minute timeout). CLAUDE.md and the /unit-test skill already say
# this in prose, but a prose instruction can be lost to context compaction —
# this hook is a deterministic backstop (see MS-708/MS-706).
set -euo pipefail

command=$(jq -r '.tool_input.command // ""')

# Strip heredoc bodies (e.g. `cat <<'EOF' ... EOF`) before scanning — commit
# messages and PR bodies routinely mention "gradlew" as prose inside one,
# which would otherwise false-positive this guard on its own literal text.
in_heredoc=false
delim=""
scan_target=""
while IFS= read -r line; do
    if $in_heredoc; then
        if [[ "$line" == "$delim" ]]; then
            in_heredoc=false
        fi
        continue
    fi
    if [[ "$line" =~ \<\<-?~?[\"\']?([A-Za-z_][A-Za-z0-9_]*)[\"\']?[[:space:]]*$ ]]; then
        delim="${BASH_REMATCH[1]}"
        in_heredoc=true
    fi
    scan_target+="$line"$'\n'
done <<< "$command"

if echo "$scan_target" | grep -qE 'gradlew' \
    && echo "$scan_target" | grep -qE ':(composeApp|shared):' \
    && echo "$scan_target" | grep -qiE 'test'; then
    cat <<'EOF'
{
  "hookSpecificOutput": {
    "hookEventName": "PreToolUse",
    "permissionDecision": "deny",
    "permissionDecisionReason": "Do not run ./gradlew directly against :composeApp or :shared test/compile tasks — this can consume the entire 30-minute job timeout on a cold build. Run ./scripts/run-affected-tests.sh instead; if it skips (composeApp/shared are always skipped there), that is a non-failure — CI is the authoritative gate for those modules."
  }
}
EOF
    exit 0
fi

exit 0
