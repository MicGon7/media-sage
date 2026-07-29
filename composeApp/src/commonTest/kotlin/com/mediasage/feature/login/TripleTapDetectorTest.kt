package com.mediasage.feature.login

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TestTimeSource

class TripleTapDetectorTest {

    @Test
    fun thirdTapWithinWindowTriggers() {
        val timeSource = TestTimeSource()
        val detector = TripleTapDetector(window = 600.milliseconds)

        assertFalse(detector.registerTap(timeSource.markNow()))
        timeSource += 100.milliseconds
        assertFalse(detector.registerTap(timeSource.markNow()))
        timeSource += 100.milliseconds
        assertTrue(detector.registerTap(timeSource.markNow()))
    }

    @Test
    fun singleTapDoesNotTrigger() {
        val timeSource = TestTimeSource()
        val detector = TripleTapDetector(window = 600.milliseconds)

        assertFalse(detector.registerTap(timeSource.markNow()))
    }

    @Test
    fun doubleTapDoesNotTrigger() {
        val timeSource = TestTimeSource()
        val detector = TripleTapDetector(window = 600.milliseconds)

        assertFalse(detector.registerTap(timeSource.markNow()))
        timeSource += 100.milliseconds
        assertFalse(detector.registerTap(timeSource.markNow()))
    }

    @Test
    fun tapOutsideWindowResetsCount() {
        val timeSource = TestTimeSource()
        val detector = TripleTapDetector(window = 600.milliseconds)

        assertFalse(detector.registerTap(timeSource.markNow()))
        timeSource += 100.milliseconds
        assertFalse(detector.registerTap(timeSource.markNow()))
        timeSource += 700.milliseconds
        assertFalse(detector.registerTap(timeSource.markNow()))
    }

    @Test
    fun triggersAgainAfterResetOnNextThreeTaps() {
        val timeSource = TestTimeSource()
        val detector = TripleTapDetector(window = 600.milliseconds)

        assertFalse(detector.registerTap(timeSource.markNow()))
        timeSource += 100.milliseconds
        assertFalse(detector.registerTap(timeSource.markNow()))
        timeSource += 100.milliseconds
        assertTrue(detector.registerTap(timeSource.markNow()))

        timeSource += 100.milliseconds
        assertFalse(detector.registerTap(timeSource.markNow()))
        timeSource += 100.milliseconds
        assertFalse(detector.registerTap(timeSource.markNow()))
        timeSource += 100.milliseconds
        assertTrue(detector.registerTap(timeSource.markNow()))
    }
}
