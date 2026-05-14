# MS-168: Switch TestFlight CI Runner to macos-26

## What changed

Changed `runs-on` in `.github/workflows/testflight.yml` from `macos-latest` to `macos-26`.

## Why

After MS-166 merged, the TestFlight workflow ran on `macos-latest` (Xcode 16.4 / iOS 18.5 SDK) and was rejected by App Store Connect at upload:

```
SDK version issue. This app was built with the iOS 18.5 SDK. All iOS and iPadOS apps
must be built with the iOS 26 SDK or later, included in Xcode 26 or later, in order
to be uploaded to App Store Connect or submitted for distribution.
```

Apple now requires the iOS 26 SDK for all new App Store Connect submissions. `macos-latest` maps to `macos-15` which only ships with Xcode 16.x. The `macos-26` runner ships with Xcode 26 (default: 26.2, range: 26.0.1–26.5 beta) and satisfies Apple's requirement.

## How the right runner was found

Rather than pushing a diagnostic step to the workflow and waiting 20+ minutes for a CI run, the GitHub runner-images repo (`github.com/actions/runner-images`) was checked directly — it publishes the full software list for every runner label. The answer was available in under a minute with no CI burn.

**Rule:** Before adding a diagnostic step to a workflow, check whether the question can be answered via a local command or a web lookup. The only step that genuinely requires CI is the App Store Connect upload itself.

## Lesson: ticket discipline applies to hotfixes too

This fix was identified after MS-166 merged and was a single line. A branch and PR were created before a Jira ticket existed, violating the ticket → branch → work → PR order. MS-168 was backfilled after the fact, leaving a mismatched branch name (`fix/MS-166-...`).

No size exception exists for the workflow rule. Even a one-liner needs a ticket first.

## Files changed

- `.github/workflows/testflight.yml` — `runs-on: macos-latest` → `runs-on: macos-26`
