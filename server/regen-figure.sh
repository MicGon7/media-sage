#!/bin/bash
# Usage: ./regen-figure.sh <figureId> [promptDetail] [referenceUrl]
# Example: ./regen-figure.sh 23 "Slightly rounded face." "https://example.com/watchman-nee.jpg"

FIGURE_ID=$1
PROMPT_DETAIL=${2:-""}
REFERENCE_URL=${3:-""}

SCRIPT_ARGS="--start-from=$FIGURE_ID --limit=1 --force --quality=low"
if [ -n "$REFERENCE_URL" ]; then
  SCRIPT_ARGS="$SCRIPT_ARGS --reference-url=$REFERENCE_URL"
fi

cd "$(dirname "$0")/.."
./gradlew :server:generateImages --no-configuration-cache \
  -PscriptArgs="$SCRIPT_ARGS" \
  -PpromptDetail="$PROMPT_DETAIL"
