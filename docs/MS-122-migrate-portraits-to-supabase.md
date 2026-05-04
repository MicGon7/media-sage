# MS-122 — Migrate Figure Portraits to Supabase Storage

## What we built

Moved the 100 generated WebP portrait images from local disk (served by Ktor static files) to Supabase Storage, a public CDN-backed object store. Production now serves portrait images via absolute Supabase URLs (`https://<project>.supabase.co/storage/v1/object/public/portraits/<id>.webp`) instead of requiring them to live on the Railway filesystem.

## Why this was necessary

Railway's filesystem is ephemeral — it's wiped on every deploy. The generated portrait images (100 `.webp` files, ~80 KB each) were gitignored and never committed, so every deploy would start with no images. The only sustainable fix is to store them in persistent, publicly accessible object storage.

## How it works

### Upload (one-time, local)

`server/scripts/upload-portraits.sh` uploads each `.webp` from `server/generated-images/` to the `portraits` bucket in Supabase Storage:

```bash
curl -X POST "$SUPABASE_URL/storage/v1/object/portraits/$FILENAME" \
  -H "Authorization: Bearer $SUPABASE_SERVICE_KEY" \
  -H "Content-Type: image/webp" \
  --data-binary "@$FILE"
```

Required env vars: `SUPABASE_URL`, `SUPABASE_SERVICE_KEY` (the legacy service_role JWT from the "Legacy API keys" tab — new scoped secret keys are not yet supported by the Storage REST API as of May 2026).

### Startup migration

`ServerDatabase.migratePortraitUrls(supabaseUrl)` runs on server startup (inside the `launch {}` block, after `FigureSeeder.seed()` completes). It updates any figure rows where `portrait_url IS NULL` to the corresponding Supabase URL:

```kotlin
"$supabaseUrl/storage/v1/object/public/portraits/$id.webp"
```

This is a no-op after the first successful deploy (URLs are already set), so it's safe to run on every boot.

### BFF URL resolution

`FigureRepository.resolveUrl()` handles both URL shapes:

- Relative (`/images/figures/1.png`) → prepends `BASE_URL` (local dev with static files)
- Absolute (`https://...`) → passes through unchanged (Supabase or any CDN)

This forward-compatible design meant zero client changes when switching from local static files to Supabase Storage.

## Key decisions

**Supabase Storage over S3 / Cloudinary**: The project already targets Supabase for future auth and Postgres work. Keeping storage in the same platform reduces vendor count and auth surface area.

**Public bucket**: Portrait images are not sensitive. A public bucket means no signed URL expiry, no token refresh logic, and global CDN caching via Cloudflare — simpler and faster.

**Startup migration vs. SQL script**: Rather than a one-off SQL migration file, the `migratePortraitUrls()` function runs on startup and is self-healing. If a new figure is added to the seeder with a null portrait URL, the next deploy will automatically patch it.

**Legacy service_role JWT**: As of May 2026, the new scoped Supabase secret keys return `403 Invalid Compact JWS` when used with the Storage REST API. The legacy JWT (under Project Settings → API → Legacy API keys) is required for uploads until Supabase updates their Storage API.

## Gotcha: migration ordering bug

The initial implementation called `migratePortraitUrls()` before `FigureSeeder.seed()`, which ran the migration against an empty table on Railway's ephemeral DB. Result: all portrait URLs remained null after deploy. Fixed in MS-124 — the migration must run *inside* the `launch {}` block, after `FigureSeeder.seed(httpClient)` completes.

## Verification

After deploy, hit the `/api/figures` endpoint. Every figure should have a non-null `portraitUrl` field pointing to `https://<project>.supabase.co/storage/v1/object/public/portraits/<id>.webp`.
