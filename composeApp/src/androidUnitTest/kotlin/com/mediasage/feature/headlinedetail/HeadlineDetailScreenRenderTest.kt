package com.mediasage.feature.headlinedetail

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
class HeadlineDetailScreenRenderTest {

    private val loadedState = HeadlineDetailContract.UiState.Success(
        headlineTitle = "Community Gardens Transform Urban Neighborhoods Across America",
        headlineSource = "Reuters",
        headlineCategory = "Community",
        headlineImageUrl = null,
        encouragement = HeadlineDetailContract.EncouragementState.Loaded(
            summary = "A wave of urban greening efforts is reshaping how residents connect with nature and each other.",
            quoteText = "You are never too old to set another goal or to dream a new dream.",
            figureName = "C.S. Lewis",
            figureRole = "Author & Apologist",
            figureImageUrl = null,
            figureBio = "Clive Staples Lewis (1898–1963) was a British writer and lay theologian, best known for The Chronicles of Narnia and Mere Christianity.",
            scriptureReference = "Philippians 4:13",
            scriptureText = "I can do all things through Christ who strengthens me.",
            matchExplanation = "Lewis's emphasis on hope and transformation mirrors the community's renewal through shared effort.",
            matchTheme = "Renewal",
        ),
        isBookmarked = false,
    )

    @Test
    fun rendersHeadlineDetailScreenLoaded() {
        captureRoboImage("build/outputs/roborazzi/headline_detail_screen_loaded.png") {
            MediaSageTheme(darkTheme = false) {
                HeadlineDetailScreen(state = loadedState, onIntent = {})
            }
        }
    }

    @Test
    fun rendersHeadlineDetailScreenLoadedDark() {
        captureRoboImage("build/outputs/roborazzi/headline_detail_screen_loaded_dark.png") {
            MediaSageTheme(darkTheme = true) {
                HeadlineDetailScreen(state = loadedState, onIntent = {})
            }
        }
    }

    // The figure profile sheet is a ModalBottomSheet — it draws in a separate window
    // that a capture of the host screen does not include, and the host's default state
    // has it dismissed. Render its stateless content composable directly, populated, so
    // the screenshot proves the feature. Rendered bare the composable has no background,
    // so this capture comes out transparent — that limitation is left visible as the
    // baseline; the faithful full-composite capture is tracked separately.
    @Test
    fun rendersFigureProfileSheetContent() {
        captureRoboImage("build/outputs/roborazzi/figure_profile_sheet_content.png") {
            MediaSageTheme(darkTheme = false) {
                FigureProfileSheetContent(
                    figureName = "C.S. Lewis",
                    figureRole = "Author & Apologist",
                    figureImageUrl = null,
                    figureBio = "Clive Staples Lewis (1898–1963) was a British writer and lay " +
                        "theologian, best known for The Chronicles of Narnia and Mere " +
                        "Christianity. He taught at Oxford and Cambridge and remains one of " +
                        "the most widely read Christian authors of the twentieth century.",
                )
            }
        }
    }
}
