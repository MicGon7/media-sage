package com.mediasage.feature.headlines

import android.app.Application
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import com.mediasage.theme.MediaSageTheme
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = Application::class)
class HeadlinesScreenRenderTest {

    @Test
    fun rendersHeadlinesFeedWithSourceCategoryAndDate() {
        captureRoboImage("build/outputs/roborazzi/headlines_screen.png") {
            MediaSageTheme {
                HeadlinesScreen(
                    state = HeadlinesContract.UiState.Success(
                        headlines = listOf(
                            HeadlineItem(
                                id = 1L,
                                articleUrl = "https://example.com/1",
                                title = "World Leaders Gather for Climate Summit in Geneva",
                                source = "Reuters",
                                category = "World",
                                snippet = "Delegates from over 190 countries convene to discuss new emissions targets.",
                                imageUrl = null,
                                publishedAtLabel = "Jun 5, 2026"
                            ),
                            HeadlineItem(
                                id = 2L,
                                articleUrl = "https://example.com/2",
                                title = "Markets Rally on Positive Economic Data",
                                source = "Financial Times",
                                category = "",
                                snippet = "Global indices rise sharply following better-than-expected jobs report.",
                                imageUrl = null,
                                publishedAtLabel = "Jun 5, 2026"
                            ),
                            HeadlineItem(
                                id = 3L,
                                articleUrl = "https://example.com/3",
                                title = "Local Parish Marks Fiftieth Anniversary",
                                source = "The New Life Times",
                                category = "Community",
                                snippet = "Congregants celebrated decades of ministry with a special service.",
                                imageUrl = null,
                                publishedAtLabel = "Jun 4, 2026",
                                isRead = true,
                                figureName = "Augustine",
                                figureRole = "Bishop of Hippo",
                                quotePreview = "Our heart is restless until it rests in Thee.",
                                isBookmarked = true
                            )
                        ),
                        todayLabel = "Friday, June 5, 2026"
                    ),
                    onIntent = {},
                    onNavigateToDetail = {}
                )
            }
        }
    }
}
