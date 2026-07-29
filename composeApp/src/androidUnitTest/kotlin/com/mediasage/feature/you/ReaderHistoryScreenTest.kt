package com.mediasage.feature.you

import android.app.Application
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import mediasage.composeapp.generated.resources.Res
import mediasage.composeapp.generated.resources.reader_history_list_empty_title
import org.jetbrains.compose.resources.getString
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = Application::class)
class ReaderHistoryScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun showsEmptyStateInCalendarViewWhenNoBriefingHistory() {
        val expectedTitle = runBlocking { getString(Res.string.reader_history_list_empty_title) }
        val emptyMonth = listOf(
            ReaderHistoryContract.CalendarDay(
                epochDay = 0L,
                dateNumber = 1,
                isToday = false,
                isFuture = false,
                hasData = false,
                figurePortraitUrl = null,
                figureName = null,
            ),
        )
        composeTestRule.setContent {
            ReaderHistoryScreen(
                state = ReaderHistoryContract.UiState.Ready(
                    viewMode = ReaderHistoryContract.ViewMode.CALENDAR,
                    calendarMonths = listOf(emptyMonth),
                ),
                onIntent = {},
            )
        }

        composeTestRule.onNodeWithText(expectedTitle).assertIsDisplayed()
    }

    @Test
    fun showsCalendarGridWhenBriefingHistoryExists() {
        val expectedTitle = runBlocking { getString(Res.string.reader_history_list_empty_title) }
        val epochDay = LocalDate(2026, 7, 1).toEpochDays().toLong()
        val monthWithData = listOf(
            ReaderHistoryContract.CalendarDay(
                epochDay = epochDay,
                dateNumber = 1,
                isToday = false,
                isFuture = false,
                hasData = true,
                figurePortraitUrl = null,
                figureName = "Augustine",
            ),
        )
        composeTestRule.setContent {
            ReaderHistoryScreen(
                state = ReaderHistoryContract.UiState.Ready(
                    viewMode = ReaderHistoryContract.ViewMode.CALENDAR,
                    calendarMonths = listOf(monthWithData),
                ),
                onIntent = {},
            )
        }

        composeTestRule.onNodeWithText("JULY 2026").assertIsDisplayed()
        composeTestRule.onAllNodesWithText(expectedTitle).assertCountEquals(0)
    }
}
