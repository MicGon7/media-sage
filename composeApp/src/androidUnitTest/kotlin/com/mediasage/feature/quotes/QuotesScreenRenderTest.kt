package com.mediasage.feature.quotes

import android.app.Application
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import com.mediasage.theme.MediaSageTheme
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Renders the Quotes screen's figure-grouped sections with sticky headers so a reviewer can see
 * the sorted, memorized-state layout without navigating the app.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = Application::class)
class QuotesScreenRenderTest {

    @Test
    fun rendersQuotesScreenWithPopulatedSections() {
        captureRoboImage("build/outputs/roborazzi/quotes_screen_populated.png") {
            MediaSageTheme {
                QuotesScreen(
                    state = QuotesContract.UiState.Success(
                        sections = listOf(
                            QuotesContract.FigureSection(
                                figureId = 1L,
                                figureName = "C.S. Lewis",
                                figureImageUrl = null,
                                quotes = listOf(
                                    QuotesContract.QuoteItem(
                                        quoteText = "You are never too old to set another goal or to dream a new dream.",
                                        isMemorized = true,
                                    ),
                                    QuotesContract.QuoteItem(
                                        quoteText = "Hardships often prepare ordinary people for an extraordinary destiny.",
                                    ),
                                ),
                            ),
                            QuotesContract.FigureSection(
                                figureId = 2L,
                                figureName = "Julian of Norwich",
                                figureImageUrl = null,
                                quotes = listOf(
                                    QuotesContract.QuoteItem(
                                        quoteText = "All shall be well, and all shall be well, and all manner of thing shall be well.",
                                    ),
                                ),
                            ),
                        ),
                    ),
                    onIntent = {},
                )
            }
        }
    }

    @Test
    fun rendersQuotesScreenEmptyState() {
        captureRoboImage("build/outputs/roborazzi/quotes_screen_empty.png") {
            MediaSageTheme {
                QuotesScreen(
                    state = QuotesContract.UiState.Success(sections = emptyList()),
                    onIntent = {},
                )
            }
        }
    }
}
