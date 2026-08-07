package com.mediasage.appserver.service

import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlin.test.Test
import kotlin.test.assertEquals

class HeadlineFetchSchedulerTest {

    @Test
    fun millisUntilNextFetchWindow_justBefore5pmUtc_returnsDelayUntil5pmSameDay() {
        val zone = TimeZone.UTC
        val nowMillis = LocalDateTime(2026, 7, 26, 16, 59, 59).toInstant(zone).toEpochMilliseconds()
        val boundaryMillis = LocalDateTime(2026, 7, 26, 17, 0, 0).toInstant(zone).toEpochMilliseconds()

        val delay = millisUntilNextFetchWindow(nowMillis, zone)

        assertEquals(boundaryMillis - nowMillis, delay)
    }

    @Test
    fun millisUntilNextFetchWindow_exactlyAt5pmUtc_returnsDelayUntilMidnight() {
        val zone = TimeZone.UTC
        val nowMillis = LocalDateTime(2026, 7, 26, 17, 0, 0).toInstant(zone).toEpochMilliseconds()
        val boundaryMillis = LocalDateTime(2026, 7, 27, 0, 0, 0).toInstant(zone).toEpochMilliseconds()

        val delay = millisUntilNextFetchWindow(nowMillis, zone)

        assertEquals(boundaryMillis - nowMillis, delay)
    }

    @Test
    fun millisUntilNextFetchWindow_justBeforeMidnightUtc_returnsDelayUntilMidnight() {
        val zone = TimeZone.UTC
        val nowMillis = LocalDateTime(2026, 7, 26, 23, 59, 59).toInstant(zone).toEpochMilliseconds()
        val boundaryMillis = LocalDateTime(2026, 7, 27, 0, 0, 0).toInstant(zone).toEpochMilliseconds()

        val delay = millisUntilNextFetchWindow(nowMillis, zone)

        assertEquals(boundaryMillis - nowMillis, delay)
    }

    @Test
    fun millisUntilNextFetchWindow_nonUtcTimezone_usesLocalHourNotUtcHour() {
        // 6pm UTC is only 2pm in New York, so the boundary must still be local 5pm today, not
        // the (already-passed, in UTC terms) 5pm — a naive UTC-hour check would wrongly roll to midnight.
        val newYork = TimeZone.of("America/New_York")
        val nowMillis = LocalDateTime(2026, 7, 26, 18, 0, 0).toInstant(TimeZone.UTC).toEpochMilliseconds()
        val boundaryMillis = LocalDateTime(2026, 7, 26, 17, 0, 0).toInstant(newYork).toEpochMilliseconds()

        val delay = millisUntilNextFetchWindow(nowMillis, newYork)

        assertEquals(boundaryMillis - nowMillis, delay)
    }

    @Test
    fun millisUntilNextFetchWindow_acrossDstSpringForward_accountsForTheMissingHour() {
        // 2026-03-08 is the US DST spring-forward date: clocks jump from 2am to 3am local time,
        // so the wall-clock gap from just-after-midnight to 5pm is only 15 (not 16.98...) hours.
        val newYork = TimeZone.of("America/New_York")
        val nowMillis = LocalDateTime(2026, 3, 8, 0, 30, 0).toInstant(newYork).toEpochMilliseconds()
        val boundaryMillis = LocalDateTime(2026, 3, 8, 17, 0, 0).toInstant(newYork).toEpochMilliseconds()

        val delay = millisUntilNextFetchWindow(nowMillis, newYork)

        assertEquals(boundaryMillis - nowMillis, delay)
    }
}
