package com.mediasage.feature.login

import kotlin.time.ComparableTimeMark
import kotlin.time.Duration

/** Tracks taps and reports true only when [targetCount] taps land within [window] of each other. */
class TripleTapDetector(
    private val window: Duration,
    private val targetCount: Int = 3,
) {
    private var tapCount = 0
    private var lastTapMark: ComparableTimeMark? = null

    fun registerTap(now: ComparableTimeMark): Boolean {
        tapCount = if (lastTapMark?.let { now - it <= window } == true) tapCount + 1 else 1
        lastTapMark = now
        if (tapCount >= targetCount) {
            tapCount = 0
            lastTapMark = null
            return true
        }
        return false
    }
}
