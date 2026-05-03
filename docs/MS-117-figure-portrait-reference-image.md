# MS-117: Figure Portrait Reference Image Support

## Outcome: Closed — use ChatGPT UI for edge cases

After thorough investigation, the OpenAI `/v1/images/edits` multipart endpoint rejects `quality`, `output_format`, and `output_compression` as unknown parameters when called with gpt-image-2. This makes it impossible to generate WebP output with quality control via the API for reference-image-guided portraits.

## What was investigated

| Approach | Result |
|---|---|
| Multipart with `quality` param | ❌ 400 Unknown parameter |
| Multipart with `output_format` param | ❌ 400 Unknown parameter |
| JSON body with `image_url` | ❌ No JSON body mode exists — SDK always uses multipart |
| OpenAI Python SDK source | Confirmed: edits endpoint is multipart-only |

The OpenAI documentation and SDK reference list these params as supported, but empirical testing proves otherwise for gpt-image-2.

## Recommended approach for edge cases

Use the **ChatGPT UI** directly:

1. Open ChatGPT and attach the reference photo
2. Use the exact prompt below (substituting the figure's data)
3. Download the result as `.png`
4. Convert to WebP: `cwebp ~/Downloads/figure.png -o server/generated-images/{id}.webp`

## Portrait prompt template

```
A formal portrait of {figureName} ({lifespan}), {figureRole}, {century} century.
Painted in the style of a Renaissance oil painting.
Rich textures, soft candlelit lighting, dignified and contemplative expression.
Chest-up composition against a dark neutral background.
Historical Christian figure rendered in a classic master's style.
Modest, period-appropriate attire. No exposed neckline or décolletage.
No text, no frames, no borders.
```

Get figure data with:
```bash
sqlite3 $DB_PATH "SELECT id, name, role, century, lifespan FROM figures WHERE name LIKE '%FigureName%';"
```

## What changed in this ticket

- `ImageGenerationService.kt` — removed `generateWithReference()` and dead imports (edits endpoint, multipart)
- `GenerateFigureImages.kt` — refactored args into `GenerationConfig` data class; extracted `saveImage()` helper
- `regen-figure.sh` — fixed `cd` to project root so script works from any directory
