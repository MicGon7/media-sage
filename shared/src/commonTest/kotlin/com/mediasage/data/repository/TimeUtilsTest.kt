package com.mediasage.data.repository

import kotlinx.datetime.TimeZone
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

class TimeUtilsTest {

    @Test
    fun localEpochDay_lateEveningInAWesternTimezone_staysOnTheLocalCalendarDay() {
        // Regression test for the MS-661 bug: 10:55pm EDT on July 26 is already 2:55am UTC on
        // July 27 — the naive UTC calculation (epochMillis / 86_400_000) would file this instant
        // under July 27, a day ahead of the real local date, silently breaking any lookup keyed by
        // "today" (e.g. DailyReflectionRepositoryImpl's lock check).
        val tenFiftyFivePmEdtOnJuly26 = 1785120900000L // 2026-07-27T02:55:00Z == 2026-07-26T22:55:00-04:00
        val newYork = TimeZone.of("America/New_York")

        val epochDay = localEpochDay(tenFiftyFivePmEdtOnJuly26, newYork)

        assertEquals(LocalDate(2026, 7, 26).toEpochDays().toLong(), epochDay)
    }

    @Test
    fun localEpochDay_matchesLocalDateInUtc() {
        val noonJuly26Utc = 1785067200000L // 2026-07-26T12:00:00Z
        val utc = TimeZone.UTC

        val epochDay = localEpochDay(noonJuly26Utc, utc)

        assertEquals(LocalDate(2026, 7, 26).toEpochDays().toLong(), epochDay)
    }
}
