package com.mediasage.feature.settings

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
class SettingsScreenRenderTest {

    @Test
    fun rendersDefaultTextScale() {
        captureRoboImage("build/outputs/roborazzi/settings_screen_text_scale_default.png") {
            MediaSageTheme {
                SettingsScreen(
                    state = SettingsContract.UiState.Ready(textScalePercent = 100),
                    onIntent = {},
                )
            }
        }
    }

    @Test
    fun rendersLargeTextScale() {
        captureRoboImage("build/outputs/roborazzi/settings_screen_text_scale_large.png") {
            MediaSageTheme(textScalePercent = 130) {
                SettingsScreen(
                    state = SettingsContract.UiState.Ready(textScalePercent = 130),
                    onIntent = {},
                )
            }
        }
    }
}
