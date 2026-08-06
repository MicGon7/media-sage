package com.mediasage.feature.briefing

import com.mediasage.data.repository.epochMillis
import kotlinx.coroutines.delay
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

/**
 * Suspends until the Briefing card's tone (morning/evening) next flips at the 5pm or midnight
 * local-time boundary. Injectable so [BriefingViewModel] can react at the exact transition
 * instant instead of polling, and so tests can simulate a crossing without a real multi-hour wait.
 */
interface BriefingToneScheduler {
    suspend fun awaitNextToneBoundary()
}

class RealBriefingToneScheduler : BriefingToneScheduler {
    override suspend fun awaitNextToneBoundary() {
        delay(millisUntilNextToneBoundary())
    }
}

internal const val TONE_BOUNDARY_HOUR = 17

internal fun millisUntilNextToneBoundary(
    nowMillis: Long = epochMillis(),
    zone: TimeZone = TimeZone.currentSystemDefault(),
): Long {
    val now = Instant.fromEpochMilliseconds(nowMillis).toLocalDateTime(zone)
    val boundary = if (now.hour < TONE_BOUNDARY_HOUR) {
        LocalDateTime(now.date, LocalTime(TONE_BOUNDARY_HOUR, 0))
    } else {
        LocalDateTime(now.date.plus(1, DateTimeUnit.DAY), LocalTime(0, 0))
    }
    return boundary.toInstant(zone).toEpochMilliseconds() - nowMillis
}

/**
 * Millis until the next local 5pm instant only — never midnight. Used by
 * [AndroidBriefingNotificationScheduler][com.mediasage.feature.briefing] to arm an `AlarmManager`
 * alarm: unlike iOS's `UNCalendarNotificationTrigger`, AlarmManager has no "next occurrence of this
 * wall-clock time" primitive, so the target instant has to be computed by hand.
 */
internal fun millisUntilNext5pm(
    nowMillis: Long = epochMillis(),
    zone: TimeZone = TimeZone.currentSystemDefault(),
): Long {
    val now = Instant.fromEpochMilliseconds(nowMillis).toLocalDateTime(zone)
    val todayBoundary = LocalDateTime(now.date, LocalTime(TONE_BOUNDARY_HOUR, 0))
    val boundary = if (now < todayBoundary) {
        todayBoundary
    } else {
        LocalDateTime(now.date.plus(1, DateTimeUnit.DAY), LocalTime(TONE_BOUNDARY_HOUR, 0))
    }
    return boundary.toInstant(zone).toEpochMilliseconds() - nowMillis
}
