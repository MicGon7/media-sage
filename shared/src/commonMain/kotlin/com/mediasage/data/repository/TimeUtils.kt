package com.mediasage.data.repository

import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

fun epochMillis(): Long = currentTimeMillis()

/**
 * The epoch day of the local calendar date at [epochMillis] — never the naive UTC day
 * (`epochMillis / 86_400_000`), which drifts a day ahead of the local date every evening in any
 * timezone behind UTC (the Americas) once UTC has already rolled over to tomorrow.
 */
fun localEpochDay(epochMillis: Long, timeZone: TimeZone = TimeZone.currentSystemDefault()): Long =
    Instant.fromEpochMilliseconds(epochMillis).toLocalDateTime(timeZone).date.toEpochDays().toLong()
