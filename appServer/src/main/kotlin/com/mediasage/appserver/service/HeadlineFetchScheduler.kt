package com.mediasage.appserver.service

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

internal const val FETCH_WINDOW_BOUNDARY_HOUR = 17

/**
 * Millis until the next twice-daily fetch window (local 5pm/midnight), mirroring
 * [com.mediasage.feature.briefing.millisUntilNextToneBoundary]'s morning/evening cadence.
 */
internal fun millisUntilNextFetchWindow(
    nowMillis: Long = System.currentTimeMillis(),
    zone: TimeZone = TimeZone.currentSystemDefault(),
): Long {
    val now = Instant.fromEpochMilliseconds(nowMillis).toLocalDateTime(zone)
    val boundary = if (now.hour < FETCH_WINDOW_BOUNDARY_HOUR) {
        LocalDateTime(now.date, LocalTime(FETCH_WINDOW_BOUNDARY_HOUR, 0))
    } else {
        LocalDateTime(now.date.plus(1, DateTimeUnit.DAY), LocalTime(0, 0))
    }
    return boundary.toInstant(zone).toEpochMilliseconds() - nowMillis
}

/**
 * Launches the twice-daily headline fetch loop: an immediate fetch so the cache is populated
 * on deploy, then one fetch per subsequent 5pm/midnight boundary for as long as [scope] is active.
 */
fun CoroutineScope.launchHeadlineFetchLoop(service: HeadlineFetchService) = launch(Dispatchers.IO) {
    service.fetchAndStoreAll()
    while (isActive) {
        delay(millisUntilNextFetchWindow())
        service.fetchAndStoreAll()
    }
}
