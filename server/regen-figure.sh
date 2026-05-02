#!/bin/bash
# Usage: ./regen-figure.sh <figureId> [promptDetail]
# Example: ./regen-figure.sh 23 "Slightly rounded face. No glasses."

FIGURE_ID=$1
PROMPT_DETAIL=${2:-""}

./gradlew :server:generateImages --no-configuration-cache \
  -PscriptArgs="--start-from=$FIGURE_ID --limit=1 --force --quality=low" \
  -PpromptDetail="$PROMPT_DETAIL"
