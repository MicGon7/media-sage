package com.mediasage.feature.briefing

import android.app.Application
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import com.mediasage.theme.MediaSageTheme
import com.mediasage.ui.ErrorType
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = Application::class)
class BriefingScreenRenderTest {

    @Test
    fun rendersErrorDialog() {
        captureRoboImage("build/outputs/roborazzi/briefing_screen_error.png") {
            MediaSageTheme {
                BriefingScreen(
                    state = BriefingContract.UiState.Error(errorType = ErrorType.NETWORK),
                    onIntent = {},
                )
            }
        }
    }

    @Test
    fun rendersPopulatedBriefingCard() {
        captureRoboImage("build/outputs/roborazzi/briefing_screen_populated.png") {
            MediaSageTheme {
                BriefingScreen(
                    state = BriefingContract.UiState.Success(
                        todayLabel = "Friday, June 5, 2026",
                        card = BriefingContract.CardState.Ready(
                            figureId = 1L,
                            figureName = "C.S. Lewis",
                            figureImageUrl = null,
                            scriptureReference = "Romans 8:28",
                            scriptureText = "And we know that in all things God works for the good of those who love him.",
                            insight = "Even setbacks are woven into a larger, purposeful story.",
                            implication = "Trust that today's difficulty is not the whole story.",
                            inspiration = "Hardships often prepare ordinary people for an extraordinary destiny.",
                            sources = listOf("Schools Nationwide Integrate Compassion Into Core Curriculum"),
                            tone = "Encouraging",
                            theme = "Faith",
                        ),
                    ),
                    onIntent = {},
                )
            }
        }
    }
}
