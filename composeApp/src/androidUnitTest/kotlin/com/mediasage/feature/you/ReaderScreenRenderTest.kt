package com.mediasage.feature.you

import android.app.Application
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import com.mediasage.theme.MediaSageTheme
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Renders the Reader screen in both week-strip and expanded-month layouts so a reviewer can see
 * the calendar with future-day scheduling removed (MS-633): future cells render with no data and
 * no picker affordance, while past/today cells keep showing the reporter that actually ran.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = Application::class)
class ReaderScreenRenderTest {

    @Test
    fun rendersReaderScreenWeekView() {
        captureRoboImage("build/outputs/roborazzi/reader_screen_week.png") {
            MediaSageTheme {
                ReaderScreen(
                    state = ReaderContract.UiState.Ready(
                        weekSlots = sampleWeekSlots(),
                        quoteCard = sampleQuoteCard(),
                        calendarDays = sampleCalendarDays(),
                    ),
                    onIntent = {},
                )
            }
        }
    }

    @Test
    fun rendersReaderScreenMonthView() {
        captureRoboImage("build/outputs/roborazzi/reader_screen_month.png") {
            MediaSageTheme {
                ReaderScreen(
                    state = ReaderContract.UiState.Ready(
                        weekSlots = sampleWeekSlots(),
                        quoteCard = sampleQuoteCard(),
                        calendarDays = sampleCalendarDays(),
                        isCalendarExpanded = true,
                    ),
                    onIntent = {},
                )
            }
        }
    }
}

private fun sampleWeekSlots(): List<ReaderContract.DaySlot> {
    val today = LocalDate(2026, 7, 22)
    val startOfWeek = today.minus(today.dayOfWeek.ordinal, DateTimeUnit.DAY)
    val names = listOf("Augustine of Hippo", "Teresa of Ávila", null, "C.S. Lewis", null, null, null)
    return (0..6).map { index ->
        val date = startOfWeek.plus(index, DateTimeUnit.DAY)
        ReaderContract.DaySlot(
            dayOfWeek = date.dayOfWeek,
            epochDay = date.toEpochDays().toLong(),
            isToday = date == today,
            assignedFigureName = names[index],
        )
    }
}

private fun sampleQuoteCard() = ReaderContract.QuoteCard(
    quoteText = "You can't go back and change the beginning, but you can start where you are and change the ending.",
    figureName = "C.S. Lewis",
    figureRole = "Author & Apologist",
    figureImageUrl = null,
    figureId = 1L,
)

private fun sampleCalendarDays(): List<ReaderContract.CalendarDay> {
    val monthStart = LocalDate(2026, 7, 1)
    val monthStartEpoch = monthStart.toEpochDays().toLong()
    val daysInMonth = monthStart.plus(1, DateTimeUnit.MONTH).toEpochDays() - monthStart.toEpochDays()
    val todayEpoch = LocalDate(2026, 7, 22).toEpochDays().toLong()
    return (0 until daysInMonth).map { d ->
        val epochDay = monthStartEpoch + d
        ReaderContract.CalendarDay(
            epochDay = epochDay,
            dateNumber = LocalDate.fromEpochDays(epochDay.toInt()).dayOfMonth,
            isToday = epochDay == todayEpoch,
            isFuture = epochDay > todayEpoch,
            hasData = epochDay <= todayEpoch,
            figurePortraitUrl = null,
            figureName = if (epochDay <= todayEpoch) "Augustine of Hippo" else null,
        )
    }
}
