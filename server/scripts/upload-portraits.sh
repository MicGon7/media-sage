#!/bin/bash
# Uploads all WebP portraits to Supabase Storage and prints the public URL for each.
# Requires SUPABASE_URL and SUPABASE_ANON_KEY env vars.
#
# Usage: ./server/scripts/upload-portraits.sh

set -e

BUCKET="portraits"
IMAGES_DIR="$(dirname "$0")/../generated-images"

if [ -z "$SUPABASE_URL" ] || [ -z "$SUPABASE_SERVICE_KEY" ]; then
  echo "ERROR: SUPABASE_URL and SUPABASE_SERVICE_KEY must be set."
  exit 1
fi

echo "=== Uploading portraits to Supabase Storage ==="
echo "Bucket : $BUCKET"
echo "Source : $IMAGES_DIR"
echo ""

SUCCESS=0
FAIL=0

for FILE in "$IMAGES_DIR"/*.webp; do
  FILENAME=$(basename "$FILE")
  FIGURE_ID="${FILENAME%.webp}"

  HTTP_STATUS=$(curl -s -o /dev/null -w "%{http_code}" \
    -X POST \
    "$SUPABASE_URL/storage/v1/object/$BUCKET/$FILENAME" \
    -H "Authorization: Bearer $SUPABASE_SERVICE_KEY" \
    -H "Content-Type: image/webp" \
    --data-binary "@$FILE")

  if [ "$HTTP_STATUS" = "200" ] || [ "$HTTP_STATUS" = "201" ]; then
    echo "✓  [$FIGURE_ID] $SUPABASE_URL/storage/v1/object/public/$BUCKET/$FILENAME"
    SUCCESS=$((SUCCESS + 1))
  else
    echo "✗  [$FIGURE_ID] HTTP $HTTP_STATUS"
    FAIL=$((FAIL + 1))
  fi
done

echo ""
echo "=== Complete === Uploaded: $SUCCESS  Failed: $FAIL"
