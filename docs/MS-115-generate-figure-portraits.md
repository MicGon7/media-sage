# MS-115: Generate Figure Portraits via gpt-image-2

## What changed

Added a one-time batch script and a new `ImageGenerationService` that generates Renaissance oil painting portraits for all 100 figures using OpenAI's `gpt-image-2` model. Images are served by Ktor as a static route.

## Architecture decision: script vs seeder

This follows the same philosophy as the SQL seed script — it is a one-time CLI tool, not production server code. The `GenerateFigureImages.kt` script lives in `scripts/` and is invoked manually from the terminal. It is NOT called at server startup or request time.

The key distinction that separates this from the "programmatic seeder" mistake:

| Pattern | Runs when | Where it lives |
|---|---|---|
| FigureSeeder (mistake) | Server startup | Production code path |
| seed_quotes.sql (correct) | Manually, once | `resources/` |
| GenerateFigureImages.kt (correct) | Manually, once | `scripts/` |

## Image format: WebP over PNG

`gpt-image-2` supports `output_format: webp` with `output_compression` (0–100). WebP at 85% compression gives ~60–70% smaller files than PNG with no visible quality loss. This matters for mobile bandwidth and future web implementations.

**API parameters used:**
- `response_format: b64_json` — how the response is encoded (base64)
- `output_format: webp` — the actual image format
- `output_compression: 85` — quality/size balance

## Two generation paths

1. **With Wikimedia reference** → `/v1/images/edits` (multipart/form-data)
   - Downloads the Wikimedia portrait, passes it as the reference image
   - Higher fidelity to the real historical person
2. **Text-only fallback** → `/v1/images/generations` (JSON)
   - Used when no Wikimedia portrait is available (~25-30 figures)
   - GPT-Image-2 uses its training knowledge of the figure

## Portrait prompt

```
A formal portrait of {figureName} ({lifespan}), {figureRole}, {century} century.
Painted in the style of a Renaissance oil painting.
Rich textures, soft candlelit lighting, dignified and contemplative expression.
Chest-up composition against a dark neutral background.
Historical Christian figure rendered in a classic master's style.
No text, no frames, no borders.
```

## Cost (gpt-image-2, 1024×1024)

| Quality | Per image | 100 figures |
|---------|-----------|-------------|
| Low     | $0.006    | $0.60       |
| Medium  | $0.053    | $5.30       |
| High    | $0.211    | $21.10      |

**Recommended workflow:** test batch of 5 at low quality ($0.03), review, then commit to medium for all 100 ($5.30).

## How to run

Always include `--no-configuration-cache` so Gradle picks up your `-P` args fresh instead of reusing a cached run.

```bash
# Dry run — see cost estimate, no API calls
./gradlew :server:generateImages --no-configuration-cache -PscriptArgs="--dry-run"

# Test exactly 5 figures at low quality ($0.03) — review output before committing to all 100
./gradlew :server:generateImages --no-configuration-cache -PscriptArgs="--limit=5 --quality=low"

# Full run at low quality (~$0.60 for 100 figures)
./gradlew :server:generateImages --no-configuration-cache -PscriptArgs="--quality=low"

# Resume from a specific figure ID (if interrupted)
./gradlew :server:generateImages --no-configuration-cache -PscriptArgs="--quality=low --start-from=21"

# Re-generate a single figure (e.g. figure 25) even if portraitUrl already set
./gradlew :server:generateImages --no-configuration-cache -PscriptArgs="--start-from=25 --limit=1 --force --quality=low"
```

Requires `DB_PATH` and `OPENAI_API_KEY` env vars to be set.

## Serving images

The server serves generated images via:
```kotlin
staticFiles("/images/figures", File("generated-images"))
```

A figure with `id=1` gets its portrait at `/images/figures/1.webp`. The `figures.portraitUrl` column is updated by the script to `/images/figures/{id}.webp`.

Generated images are gitignored (`server/generated-images/*.webp`). The directory itself is tracked via `.gitkeep`.

## Rate limiting

OpenAI Tier 1: 5 requests/minute. The script pauses 65 seconds between batches. Configurable via `--batch-size`.
