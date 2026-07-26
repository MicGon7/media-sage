package com.mediasage.feature.daydetail

import android.app.Application
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import com.mediasage.theme.MediaSageTheme
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Renders the pushed day-detail screen with both a morning and evening briefing (morning expanded),
 * so a reviewer can confirm the collapsible Morning/Evening sections and the briefing-card layout.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = Application::class)
class DayDetailScreenRenderTest {

    @Test
    fun rendersDayDetailScreen() {
        captureRoboImage("build/outputs/roborazzi/day_detail_screen.png") {
            MediaSageTheme {
                DayDetailScreen(
                    state = sampleState(),
                    onIntent = {},
                )
            }
        }
    }
}

private fun sampleState(): DayDetailContract.UiState.Ready = DayDetailContract.UiState.Ready(
    epochDay = 20289L,
    figureName = "Augustine of Hippo",
    figureImageUrl = null,
    expandedTone = "morning",
    briefings = listOf(
        DayDetailContract.BriefingSummary(
            scriptureReference = "John 3:16",
            scriptureText = "For God so loved the world that he gave his only Son",
            insight = "The Lord's love is not abstract — it moved him to act decisively.",
            implication = "Our own love for others should move us to action, not just sentiment.",
            inspiration = "Let today's headlines remind you of a love that already reached you first.",
            sources = listOf("Mere Christianity, Book IV"),
            tone = "morning",
            theme = "LOVE",
        ),
        DayDetailContract.BriefingSummary(
            scriptureReference = "Psalm 23:1",
            scriptureText = "The Lord is my shepherd, I lack nothing",
            insight = "Contentment is a fruit of trust, not of circumstance.",
            implication = "Anxiety about the day's news can coexist with settled trust.",
            inspiration = "Rest tonight in the same shepherding care that carried you through today.",
            sources = emptyList(),
            tone = "evening",
            theme = "HOPE",
        ),
    ),
)
