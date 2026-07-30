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
 * Renders the Reader screen's week strip, quote card, and History entry point so a reviewer can
 * see the recurring weekly schedule without the read-only calendar browsing surface.
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
                        userDisplayName = "Jordan",
                    ),
                    onIntent = {},
                )
            }
        }
    }

    @Test
    fun rendersReaderScreenEmptyState() {
        captureRoboImage("build/outputs/roborazzi/reader_screen_empty.png") {
            MediaSageTheme {
                ReaderScreen(
                    state = ReaderContract.UiState.Ready(
                        weekSlots = sampleWeekSlots(),
                        userDisplayName = "Jordan",
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
