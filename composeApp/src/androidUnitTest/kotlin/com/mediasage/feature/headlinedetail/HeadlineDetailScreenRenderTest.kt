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

    private val loadedEncouragement = HeadlineDetailContract.EncouragementState.Loaded(
        summary = "This article explores how communities are finding hope amid adversity through ancient wisdom.",
        quoteText = "All shall be well, and all shall be well, and all manner of thing shall be well.",
        figureName = "Julian of Norwich",
        figureRole = "Mystic & Theologian",
        figureImageUrl = null,
        scriptureReference = "Romans 8:28",
        scriptureText = "And we know that in all things God works for the good of those who love him.",
        matchExplanation = "Julian's vision of divine love mirrors the hope expressed in this passage.",
        matchTheme = "Hope in Adversity",
        tone = "contemplative",
    )

    @Test
    fun rendersHeadlineDetailScreenWithEncouragementLoaded() {
        captureRoboImage("build/outputs/roborazzi/headline_detail_loaded.png") {
            MediaSageTheme {
                HeadlineDetailScreen(
                    state = HeadlineDetailContract.UiState.Success(
                        headlineTitle = "Communities Find Ancient Wisdom in Modern Challenges",
                        headlineSource = "The Atlantic",
                        headlineCategory = "Faith & Culture",
                        headlineImageUrl = null,
                        encouragement = loadedEncouragement,
                    ),
                    onIntent = {}
                )
            }
        }
    }

    @Test
    fun rendersHeadlineDetailScreenWithFigureProfileSheet() {
        captureRoboImage("build/outputs/roborazzi/headline_detail_figure_sheet.png") {
            MediaSageTheme {
                HeadlineDetailScreen(
                    state = HeadlineDetailContract.UiState.Success(
                        headlineTitle = "Communities Find Ancient Wisdom in Modern Challenges",
                        headlineSource = "The Atlantic",
                        headlineCategory = "Faith & Culture",
                        headlineImageUrl = null,
                        encouragement = loadedEncouragement,
                        figureProfile = HeadlineDetailContract.FigureProfileState(
                            name = "Julian of Norwich",
                            role = "Mystic & Theologian",
                            imageUrl = null,
                            bio = "Julian of Norwich (c. 1342–c. 1416) was an English anchorite of the Middle Ages " +
                                "and an important Christian mystic. She is the earliest known woman to write in the " +
                                "English language.",
                        ),
                    ),
                    onIntent = {}
                )
            }
        }
    }
}
