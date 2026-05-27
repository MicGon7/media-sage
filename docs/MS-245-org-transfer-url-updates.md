# MS-245 — Update Hardcoded Repo URLs After Org Transfer to michael-gonzalez-dev

## Why the Transfer Happened

GitHub merge queue requires an org-owned repository. The repo was transferred from
`MicGon7/media-sage` (personal account) to `michael-gonzalez-dev/media-sage` (org) to
unlock merge queue for MS-235 (auto-resolve conflicts on agent PRs).

## What Changed in Code

Three files had hardcoded references to the old `MicGon7/media-sage` URL:

| File | Change |
|---|---|
| `agent/entrypoint.sh` | Clone URL updated to `michael-gonzalez-dev/media-sage` |
| `agent/worker-entrypoint.sh` | Clone URL updated to `michael-gonzalez-dev/media-sage` |
| `server/.../ArticleScraperService.kt` | User agent string updated |

Both entrypoints now use `x-access-token:${GITHUB_TOKEN}` (GitHub App auth from MS-240)
with the new org URL.

## Manual Steps Completed

**GitHub App (`media-sage-worker`)**
- Transferred from personal account (`MicGon7`) to `michael-gonzalez-dev` org
- Installed on `media-sage` repo under the org
- App is now managed at: `github.com/organizations/michael-gonzalez-dev/settings/developer-settings/apps`

**GitHub Webhooks**
- Preserved automatically after repo transfer
- Still configured at the repo level (not org level) — repo-level is correct since
  org-level webhooks fire for all repos in the org

**Railway**
- No hardcoded repo URLs in env vars — the URL was only in the entrypoint scripts

**Local git remote**
- Updated from `https://github.com/MicGon7/media-sage.git` to
  `https://github.com/michael-gonzalez-dev/media-sage.git`
- GitHub redirects the old URL automatically, but explicit update avoids relying on redirects

## Key Learning

After a GitHub repo transfer:
1. GitHub redirects old URLs automatically — clone/push/pull continue working temporarily
2. Hardcoded URLs in scripts and code still need updating — redirects are not permanent guarantees
3. GitHub Apps installed on a personal account cannot see repos that move to an org — the app
   must be transferred to the org and re-installed
4. Webhooks are preserved at the repo level after transfer — no reconfiguration needed
5. Org-level developer settings are separate from personal account developer settings
