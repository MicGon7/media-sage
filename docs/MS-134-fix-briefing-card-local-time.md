# MS-134: Fix briefing card tone using device local time

## Bug

The briefing card tone ("morning" / "evening") was derived from UTC time, not the device's local time. Users in timezones behind UTC would see the wrong tone — e.g. a user in EDT (UTC-4) at 9 AM local time got "evening" because the UTC hour was 13.

## Root Cause

```kotlin
// BEFORE — broken
private fun currentTone(): String {
    val hourUtc = (epochMillis() % 86400000L / 3600000L).toInt()
    return if (hourUtc < 12) "morning" else "evening"
}
```

`epochMillis()` returns raw UTC epoch millis. Dividing to extract the hour gives a UTC hour, not the local hour.

## Fix

Added `kotlinx-datetime 0.7.1` and replaced the manual UTC math with a proper local time lookup:

```kotlin
// AFTER — correct
private fun currentTone(): String {
    val hour = Instant.fromEpochMilliseconds(epochMillis())
        .toLocalDateTime(TimeZone.currentSystemDefault()).hour
    return if (hour < 12) "morning" else "evening"
}
```

`TimeZone.currentSystemDefault()` reads the device's configured timezone on both Android and iOS.

## kotlinx-datetime API note (Kotlin 2.x)

In `kotlinx-datetime 0.7.x`, `Clock.System` was moved to `kotlin.time.Clock.System` (stdlib) and is still `@ExperimentalTime` in Kotlin 2.3.x, requiring `@OptIn`. The clean alternative is `Instant.fromEpochMilliseconds()` combined with `toLocalDateTime()` — no `@OptIn` needed and it works with the existing `epochMillis()` helper already in the shared module.

## How to test

Change the device's system time to morning (e.g. 9 AM) and open the app — briefing card should say "morning". Change to evening (e.g. 8 PM) and force-refresh — should say "evening".
