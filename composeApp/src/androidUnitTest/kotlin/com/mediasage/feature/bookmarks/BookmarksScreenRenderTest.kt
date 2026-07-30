package com.mediasage.feature.bookmarks

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
class BookmarksScreenRenderTest {

    @Test
    fun rendersSavedHeadlinesWithSourceCategoryAndDate() {
        captureRoboImage("build/outputs/roborazzi/bookmarks_screen.png") {
            MediaSageTheme {
                BookmarksScreen(
                    state = BookmarksContract.UiState.Success(
                        items = listOf(
                            BookmarkItem(
                                articleUrl = "https://example.com/1",
                                headlineTitle = "Community Gardens Transform Urban Neighborhoods",
                                figureName = "Augustine",
                                figureRole = "Bishop of Hippo",
                                quotePreview = "Our heart is restless until it rests in Thee.",
                                headlineImageUrl = null,
                                source = "Reuters",
                                category = "Community",
                                publishedAtLabel = "Jun 5, 2026"
                            ),
                            BookmarkItem(
                                articleUrl = "https://example.com/2",
                                headlineTitle = "Markets Rally on Positive Economic Data",
                                figureName = "C.S. Lewis",
                                figureRole = "Author",
                                quotePreview = "You are never too old to dream.",
                                headlineImageUrl = null,
                                source = "Financial Times",
                                category = "",
                                publishedAtLabel = "Jun 5, 2026"
                            )
                        )
                    ),
                    onIntent = {},
                    onNavigateBack = {},
                    onNavigateToDetail = {}
                )
            }
        }
    }
}
