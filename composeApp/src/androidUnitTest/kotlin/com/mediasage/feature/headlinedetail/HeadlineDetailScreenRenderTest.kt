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

    @Test
    fun rendersFigureProfileBottomSheet() {
        captureRoboImage("build/outputs/roborazzi/headline_detail_figure_profile_sheet.png") {
            MediaSageTheme {
                HeadlineDetailScreen(
                    state = HeadlineDetailContract.UiState.Success(
                        headlineTitle = "Community Gardens Transform Urban Neighborhoods",
                        headlineSource = "Reuters",
                        headlineCategory = "Community",
                        headlineImageUrl = null,
                        encouragement = HeadlineDetailContract.EncouragementState.Loaded(
                            summary = "A local initiative is bringing neighbors together.",
                            quoteText = "Our heart is restless until it rests in Thee.",
                            figureName = "Augustine",
                            figureRole = "Bishop of Hippo",
                            figureImageUrl = null,
                            scriptureReference = "Psalm 23:1",
                            scriptureText = "The Lord is my shepherd, I shall not want.",
                            matchExplanation = "Both speak to finding rest and belonging in community.",
                            matchTheme = "Belonging",
                        ),
                        figureProfile = HeadlineDetailContract.FigureProfileState.Visible(
                            figureName = "Augustine",
                            figureRole = "Bishop of Hippo",
                            figureImageUrl = null,
                            bio = "Aurelius Augustinus (354-430) was a theologian and philosopher whose " +
                                "writings, including Confessions and The City of God, shaped Western " +
                                "Christian thought for centuries.",
                        ),
                    ),
                    onIntent = {},
                )
            }
        }
    }
}
