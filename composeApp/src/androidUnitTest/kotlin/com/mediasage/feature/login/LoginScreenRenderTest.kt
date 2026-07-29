package com.mediasage.feature.login

import android.app.Application
import androidx.compose.runtime.CompositionLocalProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import com.mediasage.LocalIsDebugBuild
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Proves the debug gate visually: the bypass button is present only when
 * [LocalIsDebugBuild] is true, and absent in the release-build render.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = Application::class, qualifiers = "w360dp-h900dp")
class LoginScreenRenderTest {

    @Test
    fun rendersLoginScreenReleaseBuildWithoutBypass() {
        captureRoboImage("build/outputs/roborazzi/login_screen_release.png") {
            CompositionLocalProvider(LocalIsDebugBuild provides false) {
                LoginScreen(state = LoginContract.UiState(), onIntent = {})
            }
        }
    }

    @Test
    fun rendersLoginScreenDebugBuildWithBypass() {
        captureRoboImage("build/outputs/roborazzi/login_screen_debug.png") {
            CompositionLocalProvider(LocalIsDebugBuild provides true) {
                LoginScreen(state = LoginContract.UiState(), onIntent = {})
            }
        }
    }
}
