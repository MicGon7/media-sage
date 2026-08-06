# MS-712: Notify the user locally when the evening Briefing tone starts

## Gap

`BriefingToneScheduler` (MS-706) only refreshes the Briefing card's tone while a `BriefingViewModel`
is alive and collecting. A user who is not in the app when the 5pm boundary passes has no signal
until they happen to reopen the Briefing screen — there is no server/push component in this app, so
nothing tells the OS to alert the user while the process may be fully backgrounded or killed.

## Fix

Added a `BriefingNotificationScheduler` interface (`onBriefingVisible()` / `onBriefingHidden()`)
with platform `expect/actual`-equivalent implementations wired through per-platform Koin modules
(`NotificationModule.android.kt` / `NotificationModule.ios.kt`), following the existing
`ThemeModule`/`DatabaseModule` per-platform-module convention rather than an `expect/actual`
function — DI wiring in this codebase is always a platform module swap, never expect/actual.

`MediaSageScaffold`'s `Route.Briefing` entry calls `onBriefingVisible()`/`onBriefingHidden()` from a
single `LifecycleResumeEffect(Unit)`. This one effect correctly captures both ways a user can leave
the Briefing screen: backgrounding the app (Activity `ON_PAUSE`) and switching bottom-nav tabs
(`MediaSageAppState.navigateToTopLevel()` calls `backStack.clear()`, which disposes the `NavEntry`
composable outright). Both cases fire `onPauseOrDispose`, so no separate `LifecycleEventObserver` or
`appState.currentDestination` coupling was needed.

`RequestNotificationPermissionEffect()` (also `expect`/platform-`actual`) is called once per
`Route.Briefing` composition to trigger each platform's native permission prompt (Android
`POST_NOTIFICATIONS` on API 33+, iOS `requestAuthorizationWithOptions`) — never a blocking gate on
the Briefing screen itself, since a denial must not break the screen's own live tone update (AC5).

### Android: `AlarmManager.setWindow`, not `setExactAndAllowWhileIdle`

Android has no "fire at this wall-clock hour, repeating daily" primitive, so
`millisUntilNext5pm()` (`BriefingToneScheduler.kt`) computes the next 5pm instant by hand, mirroring
the existing `millisUntilNextToneBoundary()`. `onBriefingHidden()` arms one `AlarmManager.setWindow`
alarm (10-minute window) targeting a `BroadcastReceiver` (`BriefingToneNotificationReceiver`);
`onBriefingVisible()` cancels it. `setWindow` was chosen specifically because it does **not** require
the "Alarms & reminders" special permission that `setExactAndAllowWhileIdle` needs on API 31+ (AC7)
— a 10-minute delivery window is well within AC6's "a few minutes" tolerance. The receiver re-checks
`POST_NOTIFICATIONS` before calling `NotificationManagerCompat.notify()`, since a user can revoke
the permission after the alarm was armed (AC5).

### iOS: `UNCalendarNotificationTrigger` with only hour/minute components

Unlike Android, iOS's `UNCalendarNotificationTrigger.triggerWithDateMatchingComponents` natively
supports "next occurrence of this wall-clock time" when given only `hour`/`minute` date components
— no manual next-instant math needed. `repeats = false` mirrors Android's one-shot-per-visit
semantic: each `onBriefingHidden()` arms exactly one trigger for the next 5pm, and leaving the
Briefing screen again re-arms it, rather than a daily-repeating trigger that would keep firing
indefinitely after a single visit. `onBriefingVisible()` removes both pending and delivered requests
for a fixed identifier so a same-day dismissal-then-reopen doesn't leave a stale notification
queued.

## Why this needed a learning doc

First local (non-push) notification integration in the app — first use of `AlarmManager`,
`UNUserNotificationCenter`, `LifecycleResumeEffect`, and `koinInject` (for a non-ViewModel dependency
inside a composable). Any future "notify while backgrounded" feature should reuse the
`BriefingNotificationScheduler` shape (visible/hidden pair backed by a per-platform Koin module)
rather than reinventing the arm/disarm lifecycle.

## How to test

Grant notification permission, open the Briefing screen, then switch to another tab (or background
the app) before 5pm local time. A notification should arrive within a few minutes of 5pm. Reopening
the Briefing screen before 5pm and staying on it should produce no notification — the live tone
update is the only signal. Denying the permission prompt should not crash the app or prevent the
Briefing screen's own tone from flipping while it's open.
