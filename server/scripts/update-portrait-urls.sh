#!/bin/bash
# Updates all figure portraitUrl values in the DB to absolute Supabase Storage URLs.
# Requires SUPABASE_URL and DB_PATH env vars.
#
# Usage: ./server/scripts/update-portrait-urls.sh

if [ -z "$SUPABASE_URL" ] || [ -z "$DB_PATH" ]; then
  echo "ERROR: SUPABASE_URL and DB_PATH must be set."
  exit 1
fi

IMAGES_DIR="$(dirname "$0")/../generated-images"
BASE_URL="$SUPABASE_URL/storage/v1/object/public/portraits"

echo "=== Updating portrait URLs in DB ==="
echo "DB   : $DB_PATH"
echo "Base : $BASE_URL"
echo ""

COUNT=0
for FILE in "$IMAGES_DIR"/*.webp; do
  FILENAME=$(basename "$FILE")
  FIGURE_ID="${FILENAME%.webp}"
  URL="$BASE_URL/$FILENAME"

  sqlite3 "$DB_PATH" "UPDATE figures SET portrait_url = '$URL' WHERE id = $FIGURE_ID;"
  COUNT=$((COUNT + 1))
done

echo "Updated $COUNT portrait URLs."
echo ""
echo "Sample:"
sqlite3 "$DB_PATH" "SELECT id, portrait_url FROM figures LIMIT 3;"
