package com.mediasage.feature.you

import android.app.Application
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import mediasage.composeapp.generated.resources.Res
import mediasage.composeapp.generated.resources.reader_briefings_empty_title
import mediasage.composeapp.generated.resources.reader_quote_empty_title
import mediasage.composeapp.generated.resources.you_recent_briefings_section_title
import org.jetbrains.compose.resources.getString
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = Application::class)
class ReaderScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun showsQuoteEmptyStateWhenQuoteCardIsMissing() {
        val expectedTitle = runBlocking { getString(Res.string.reader_quote_empty_title) }
        composeTestRule.setContent {
            ReaderScreen(
                state = ReaderContract.UiState.Ready(quoteCard = null),
                onIntent = {},
            )
        }

        composeTestRule.onNodeWithText(expectedTitle).assertIsDisplayed()
    }

    @Test
    fun showsSavedQuoteCardWhenQuoteCardIsPresent() {
        composeTestRule.setContent {
            ReaderScreen(
                state = ReaderContract.UiState.Ready(
                    quoteCard = ReaderContract.QuoteCard(
                        quoteText = "Be still and know.",
                        figureName = "Augustine",
                        figureRole = "Theologian",
                        figureImageUrl = null,
                        figureId = 1L,
                    ),
                ),
                onIntent = {},
            )
        }

        composeTestRule.onNodeWithText("— Augustine, Theologian").assertIsDisplayed()
    }

    @Test
    fun showsBriefingsEmptyStateWhenPastBriefingsIsEmpty() {
        val expectedTitle = runBlocking { getString(Res.string.reader_briefings_empty_title) }
        composeTestRule.setContent {
            ReaderScreen(
                state = ReaderContract.UiState.Ready(pastBriefings = emptyList()),
                onIntent = {},
            )
        }

        composeTestRule.onNodeWithText(expectedTitle).assertIsDisplayed()
    }

    @Test
    fun showsPastBriefingsCarouselWhenPastBriefingsIsPresent() {
        val expectedSectionTitle = runBlocking { getString(Res.string.you_recent_briefings_section_title) }
        composeTestRule.setContent {
            ReaderScreen(
                state = ReaderContract.UiState.Ready(
                    pastBriefings = listOf(
                        ReaderContract.PastBriefingCard(
                            epochDay = 0L,
                            figureName = "Augustine",
                            figureImageUrl = null,
                            inspiration = "Confessions",
                            dayLabel = ReaderContract.DayLabel.Yesterday,
                        ),
                    ),
                ),
                onIntent = {},
            )
        }

        composeTestRule.onNodeWithText(expectedSectionTitle).assertIsDisplayed()
    }
}
