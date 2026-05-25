# MS-240: Migrate media-sage-bot to GitHub App (media-sage-worker)

## What Changed

Replaced the static `GITHUB_BOT_TOKEN` PAT (a fake user account credential) with GitHub App
installation tokens issued to the `media-sage-worker` GitHub App.

Both Docker images now generate a fresh installation token at startup. The Kotlin orchestrator
additionally refreshes tokens at runtime before any `gh` CLI invocation, so the 1-hour TTL
is never a concern for long-running orchestrator processes.

## Why GitHub Apps

| | Fake bot account (old) | GitHub App (new) |
|---|---|---|
| Identity | Fake GitHub user account | First-class automated actor (`[bot]` suffix) |
| Credential | Static PAT — long-lived, broad scope | Installation token — 1-hour TTL, auto-rotating |
| Permissions | Whatever the PAT scopes allowed | Exactly what the App declares (contents: write, pull-requests: write) |
| Tied to a user? | Yes — breaks if account is suspended | No — installed at repo level |
| GitHub seat cost | Yes (in paid orgs) | No |
| Self-review blocked? | Yes — bot can't approve its own PRs | Yes — enables separate reviewer App identity |

## Architecture

### Token generation flow

```
Startup (entrypoint.sh / worker-entrypoint.sh)
    ↓
get-github-token.py
    ↓ reads: GITHUB_APP_ID, GITHUB_APP_INSTALLATION_ID, GITHUB_APP_PRIVATE_KEY_BASE64
    ↓ generates: RS256 JWT (9-min window, signed with private key)
    ↓ exchanges: POST /app/installations/{id}/access_tokens
    → installation token (1 hour)
    → export GH_TOKEN + git clone URL (x-access-token:{token}@github.com/...)

Runtime (Kotlin orchestrator — AgentLaunchService.postInlineCommentReply)
    ↓
GitHubAppTokenService.getToken()
    ↓ returns cached token if > 10 min remaining
    ↓ otherwise: new JWT → token exchange → cache
    → injected as GH_TOKEN into ProcessBuilder environment
```

### No external JWT library

`GitHubAppTokenService` uses JDK built-ins only:
- `java.security.KeyFactory` + `PKCS8EncodedKeySpec` for key loading
- `java.security.Signature` (SHA256withRSA) for signing
- `java.util.Base64` for URL-safe encoding

GitHub App private keys are PKCS#1 format (`BEGIN RSA PRIVATE KEY`). Java's `KeyFactory`
natively handles PKCS#8. `wrapInPkcs8()` converts PKCS#1 DER bytes to a PKCS#8 envelope
using the fixed RSA algorithm OID — no BouncyCastle required.

## New Environment Variables

Replace `GITHUB_BOT_TOKEN` with:

| Variable | Description |
|---|---|
| `GITHUB_APP_ID` | Numeric App ID from the GitHub App settings page |
| `GITHUB_APP_INSTALLATION_ID` | Installation ID for the media-sage repo |
| `GITHUB_APP_PRIVATE_KEY_BASE64` | RSA private key PEM, base64-encoded (same pattern as `GOOGLE_CREDENTIALS_BASE64`) |

`GITHUB_BOT_LOGIN` should now be set to `media-sage-worker[bot]` (with the `[bot]` suffix
that GitHub adds automatically for App actors).

`GITHUB_BOT_NAME` default changed from `media-sage-bot` to `media-sage-worker` (used for
`git config user.name` in commit authorship).

## Git Clone URL Pattern

GitHub App installation tokens use `x-access-token` as the username in HTTPS clone URLs:
```
https://x-access-token:{installation_token}@github.com/MicGon7/media-sage.git
```

This is the GitHub-documented pattern for App tokens — distinct from PAT-based URLs
which use the account username.

## What Was Not Changed

- Workers run for 10-30 minutes; the 1-hour token TTL covers this without credential helper support
- `GITHUB_BOT_EMAIL` — still required for `git config user.email`
- `GITHUB_WEBHOOK_SECRET` — unchanged
- All Cloud Run Job dispatch logic — unchanged

## Naming Convention Established

| App | Role |
|---|---|
| `media-sage-worker[bot]` | Implements tickets — writes code, opens PRs, pushes commits |
| `media-sage-reviewer[bot]` | Reviews PRs — approves, posts structured feedback (future: MS-241) |

Both will be registered as GitHub Apps. The reviewer App enables the review agent to formally
approve PRs opened by the worker — GitHub blocks self-review within the same App identity.
