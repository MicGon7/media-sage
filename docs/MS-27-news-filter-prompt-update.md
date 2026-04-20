# MS-27: Filter News Categories and Update Claude Prompt

**Epic:** MS-1 (Server API Layer)
**Date completed:** 2026-04-19

## What was built

Two targeted changes to refine the app's content and matching behavior.

### 1. News category filter
Added `-sports,-entertainment` category exclusion to `NewsApiService.getTopHeadlines()`. TheNewsAPI supports negative category filters with the `-` prefix.

### 2. Claude system prompt update
Expanded from "match negative/troubling headlines" to "match ALL meaningful headlines":
- Troubling news → comfort, hope, perseverance, divine sovereignty
- Positive news → celebration, peacemaking, gratitude, redemption

## Key decisions & why

- **Exclude at API level, not post-fetch**: Filtering categories in the API request is more efficient than fetching everything and filtering client-side. Saves bandwidth and API quota.
- **Broader matching philosophy**: The app isn't just about comforting bad news — it's about providing theological wisdom as a lens on all current events. A ceasefire headline deserves a peacemaking quote just as much as a disaster headline deserves a hope quote.
- **User chooses what to match**: Headlines are displayed without auto-matching. The user decides which headlines to request a match for.

## Smoke test results

- **Category filter**: No sports or entertainment articles returned
- **Positive headline**: "Historic ceasefire agreement..." matched with Francis of Assisi's peace prayer at 0.95 confidence
- **Negative headline**: "Earthquake devastates..." matched with Corrie ten Boom at 0.88 confidence (tested earlier)
