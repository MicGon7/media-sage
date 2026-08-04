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
}
