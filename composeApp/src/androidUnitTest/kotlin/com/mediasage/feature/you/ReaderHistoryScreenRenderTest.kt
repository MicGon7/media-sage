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
    fun rendersReaderHistoryScreenCalendarView() {
        captureRoboImage("build/outputs/roborazzi/reader_history_screen_calendar.png") {
            MediaSageTheme {
                ReaderHistoryScreen(
                    state = sampleCalendarState(),
                    onIntent = {},
                )
            }
        }
    }

    @Test
    fun rendersReaderHistoryScreenListView() {
        captureRoboImage("build/outputs/roborazzi/reader_history_screen_list.png") {
            MediaSageTheme {
                ReaderHistoryScreen(
                    state = sampleListState(),
                    onIntent = {},
                )
            }
        }
    }
}

private val SampleNames = listOf("Augustine of Hippo", "Teresa of Ávila", "C.S. Lewis")
private val SampleTodayEpoch = LocalDate(2026, 7, 22).toEpochDays().toLong()
private val SampleEarliestEpoch = LocalDate(2026, 5, 1).toEpochDays().toLong()

private fun sampleCalendarState(): ReaderHistoryContract.UiState.Ready {
    val currentMonth = buildSampleMonth(LocalDate(2026, 7, 1))
    val previousMonth = buildSampleMonth(LocalDate(2026, 6, 1))
    return ReaderHistoryContract.UiState.Ready(
        todayEpochDay = SampleTodayEpoch,
        earliestEpochDay = SampleEarliestEpoch,
        viewMode = ReaderHistoryContract.ViewMode.CALENDAR,
        calendarMonths = listOf(currentMonth, previousMonth),
    )
}

private fun buildSampleMonth(monthStart: LocalDate): List<ReaderHistoryContract.CalendarDay> {
    val monthStartEpoch = monthStart.toEpochDays().toLong()
    val daysInMonth = monthStart.plus(1, DateTimeUnit.MONTH).toEpochDays() - monthStart.toEpochDays()
    return (0 until daysInMonth).map { d ->
        val epochDay = monthStartEpoch + d
        val hasData = epochDay <= SampleTodayEpoch
        ReaderHistoryContract.CalendarDay(
            epochDay = epochDay,
            dateNumber = LocalDate.fromEpochDays(epochDay.toInt()).dayOfMonth,
            isToday = epochDay == SampleTodayEpoch,
            isFuture = epochDay > SampleTodayEpoch,
            hasData = hasData,
            figurePortraitUrl = null,
            figureName = if (hasData) SampleNames[(d % SampleNames.size).toInt()] else null,
        )
    }
}

private fun sampleListState(): ReaderHistoryContract.UiState.Ready {
    val listDays = (0..10).map { i ->
        val epochDay = SampleTodayEpoch - i
        ReaderHistoryContract.ListDay(
            epochDay = epochDay,
            figurePortraitUrl = null,
            figureName = SampleNames[i % SampleNames.size],
        )
    }
    return ReaderHistoryContract.UiState.Ready(
        todayEpochDay = SampleTodayEpoch,
        earliestEpochDay = SampleEarliestEpoch,
        viewMode = ReaderHistoryContract.ViewMode.LIST,
        listDays = listDays,
    )
}
