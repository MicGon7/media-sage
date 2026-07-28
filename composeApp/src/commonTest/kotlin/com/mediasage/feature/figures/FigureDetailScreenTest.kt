package com.mediasage.feature.figures

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import mediasage.composeapp.generated.resources.Res
import mediasage.composeapp.generated.resources.figure_detail_biography
import mediasage.composeapp.generated.resources.figure_detail_memorize_quote
import mediasage.composeapp.generated.resources.figure_detail_no_biography
import mediasage.composeapp.generated.resources.figure_detail_no_quotes
import mediasage.composeapp.generated.resources.figure_detail_tab_quotes
import mediasage.composeapp.generated.resources.figure_detail_tab_writings
import mediasage.composeapp.generated.resources.figure_detail_writings_placeholder
import org.jetbrains.compose.resources.getString
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class FigureDetailScreenTest {

    private val successState = FigureDetailContract.UiState.Success(
        figureName = "C.S. Lewis",
        figureRole = "Author & Apologist",
        figureImageUrl = null,
        bio = "A British writer and lay theologian.",
        quotes = listOf(
            FigureQuoteItem(
                quoteText = "We are what we believe we are.",
                headlineTitle = "New Research Links Daily Gratitude Practice to Mental Health",
            )
        ),
    )

    @Test
    fun showsBiographyTabContentByDefault() = runComposeUiTest {
        setContent {
            FigureDetailScreen(state = successState)
        }

        onNodeWithText(successState.bio.orEmpty()).assertIsDisplayed()
    }

    @Test
    fun selectingQuotesTabShowsQuotesInline() = runComposeUiTest {
        setContent {
            FigureDetailScreen(state = successState)
        }

        onNodeWithText(getString(Res.string.figure_detail_tab_quotes)).performClick()

        onNodeWithText("“We are what we believe we are.”").assertIsDisplayed()
    }

    @Test
    fun selectingWritingsTabShowsPlaceholder() = runComposeUiTest {
        setContent {
            FigureDetailScreen(state = successState)
        }

        onNodeWithText(getString(Res.string.figure_detail_tab_writings)).performClick()

        onNodeWithText(getString(Res.string.figure_detail_writings_placeholder)).assertIsDisplayed()
    }

    @Test
    fun quotesTabShowsEmptyStateWhenNoQuotes() = runComposeUiTest {
        setContent {
            FigureDetailScreen(state = successState.copy(quotes = emptyList()))
        }

        onNodeWithText(getString(Res.string.figure_detail_tab_quotes)).performClick()

        onNodeWithText(getString(Res.string.figure_detail_no_quotes)).assertIsDisplayed()
    }

    @Test
    fun biographyTabShowsEmptyStateWhenNoBio() = runComposeUiTest {
        setContent {
            FigureDetailScreen(state = successState.copy(bio = null))
        }

        onNodeWithText(getString(Res.string.figure_detail_no_biography)).assertIsDisplayed()
    }

    @Test
    fun tappingMemorizeQuoteFiresPinQuoteIntent() = runComposeUiTest {
        val firedIntents = mutableListOf<FigureDetailContract.Intent>()
        setContent {
            FigureDetailScreen(
                state = successState,
                onIntent = { firedIntents.add(it) },
            )
        }

        onNodeWithText(getString(Res.string.figure_detail_tab_quotes)).performClick()
        onNodeWithContentDescription(getString(Res.string.figure_detail_memorize_quote)).performClick()

        assertEquals(1, firedIntents.size)
        assertEquals(
            FigureDetailContract.Intent.PinQuote(successState.quotes.first().quoteText),
            firedIntents.first(),
        )
    }

    @Test
    fun switchingBackToBiographyTabAfterQuotesStillShowsBio() = runComposeUiTest {
        setContent {
            FigureDetailScreen(state = successState)
        }

        onNodeWithText(getString(Res.string.figure_detail_tab_quotes)).performClick()
        onNodeWithText(getString(Res.string.figure_detail_biography)).performClick()

        onNodeWithText(successState.bio.orEmpty()).assertIsDisplayed()
    }
}
