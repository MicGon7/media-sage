# MS-706: Refresh Briefing tone when the 5pm boundary passes

## Bug

The Briefing card's tone ("morning"/"evening") was computed once per `loadCard()` subscription,
with no input tied to wall-clock time. A user viewing the screen as the clock crossed 5pm saw no
change until some unrelated repository emission happened to occur; a user who backgrounded the
app before 5pm and reopened after saw the stale tone until the next unrelated recompute.

## Fix

Added `BriefingToneScheduler` (`BriefingToneScheduler.kt`), an interface with one suspend method,
`awaitNextToneBoundary()`, that computes the next 5pm/midnight local-time instant and suspends
until then. `BriefingViewModel` runs a `while (true) { toneScheduler.awaitNextToneBoundary(); loadCard() }`
loop in `init`, so `loadCard()` — which already recomputes `currentTone()`, `todayLabel()`, and
today's date-derived values — reruns exactly at each transition, and never on a fixed interval.

`loadCard()` now cancels its previous `Job` before relaunching, so the periodic reload (and
`Intent.Retry`) don't accumulate duplicate `collectLatest` subscriptions over the ViewModel's
lifetime.

`MediaSageScaffold`'s `Route.Briefing` entry moved from `collectAsState()` to
`collectAsStateWithLifecycle()`, matching the Now in Android recommendation for a screen whose
state can change while the app is backgrounded — collection now pauses/resumes with the lifecycle.

## Why an injectable scheduler, not a bare `delay()` in the ViewModel

A perpetual `delay()`-based coroutine (`while (true) { delay(...); ... }`) launched in
`viewModelScope` schedules on whatever dispatcher the test has installed via
`Dispatchers.setMain(testDispatcher)`. `kotlinx-coroutines-test`'s `advanceUntilIdle()` advances
virtual time until the scheduler's task queue is empty — but a perpetual delay loop always
re-schedules itself, so the queue is never empty and `advanceUntilIdle()` hangs forever. This
codebase's existing `BriefingViewModelTest` suite uses `advanceUntilIdle()` extensively, so wiring
a real periodic delay directly into `loadCard()`'s combine would have hung every existing test the
moment it was constructed with the default scheduler.

Making the scheduler an injected interface sidesteps this: production code gets
`RealBriefingToneScheduler` (a real `delay()`), while tests inject a `FakeBriefingToneScheduler`
that suspends on a `Channel` instead of a virtual-time delay. A channel receive with nothing sent
is not a scheduled task, so it never appears in `advanceUntilIdle()`'s queue — the fake simply
never fires unless the test calls `crossBoundary()` to simulate the transition. This is the
detail to carry forward: **any future "wake up periodically while a ViewModel is alive" feature
needs the same injectable-scheduler treatment**, or it will silently hang any existing test that
calls `advanceUntilIdle()` on that ViewModel's state.

## How to test

Change the device's system time to 4:59pm and open the Briefing screen; watch it flip to the
evening tone at 5:00pm without navigating away and back. Background the app before 5pm and reopen
after — the evening tone should already be showing.
