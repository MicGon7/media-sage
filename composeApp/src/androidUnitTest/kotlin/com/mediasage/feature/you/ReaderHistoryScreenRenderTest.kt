package com.mediasage.feature.you

import android.app.Application
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import com.mediasage.theme.MediaSageTheme
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Renders the read-only History calendar with a mix of past days that have reflections, today,
 * and future days so a reviewer can confirm future cells never render as having data.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = Application::class)
class ReaderHistoryScreenRenderTest {

    @Test
    fun rendersReaderHistoryScreen() {
        captureRoboImage("build/outputs/roborazzi/reader_history_screen.png") {
            MediaSageTheme {
                ReaderHistoryScreen(
                    state = sampleState(),
                    onIntent = {},
                )
            }
        }
    }
}

private fun sampleState(): ReaderHistoryContract.UiState.Ready {
    val monthStart = LocalDate(2026, 7, 1)
    val monthStartEpoch = monthStart.toEpochDays().toLong()
    val daysInMonth = monthStart.plus(1, DateTimeUnit.MONTH).toEpochDays() - monthStart.toEpochDays()
    val todayEpoch = LocalDate(2026, 7, 22).toEpochDays().toLong()
    val names = listOf("Augustine of Hippo", "Teresa of Ávila", "C.S. Lewis")
    val calendarDays = (0 until daysInMonth).map { d ->
        val epochDay = monthStartEpoch + d
        val hasData = epochDay <= todayEpoch
        ReaderHistoryContract.CalendarDay(
            epochDay = epochDay,
            dateNumber = LocalDate.fromEpochDays(epochDay.toInt()).dayOfMonth,
            isToday = epochDay == todayEpoch,
            isFuture = epochDay > todayEpoch,
            hasData = hasData,
            figurePortraitUrl = null,
            figureName = if (hasData) names[(d % names.size).toInt()] else null,
        )
    }
    return ReaderHistoryContract.UiState.Ready(
        todayEpochDay = todayEpoch,
        calendarDays = calendarDays,
    )
}
