package com.mediasage.feature.login

import android.app.Application
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Renders the login screen's remember-me switch and tappable links in both idle and loading
 * states so a reviewer can confirm the switch keeps its colors while disabled and the links
 * read as distinct from non-interactive text. LoginScreen applies its own MediaSageTheme
 * internally (always dark), so no external theme wrapper is needed here.
 *
 * The default Robolectric screen (320x470dp) is too short for the scrollable login form to
 * show the switch and links without scrolling, so this pins a taller device profile.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp", application = Application::class)
class LoginScreenRenderTest {

    @Test
    fun rendersLoginScreenIdle() {
        captureRoboImage("build/outputs/roborazzi/login_screen_idle.png") {
            LoginScreen(
                state = LoginContract.UiState(rememberedEmail = "user@example.com", rememberEmail = true),
                onIntent = {},
            )
        }
    }

    @Test
    fun rendersLoginScreenLoading() {
        captureRoboImage("build/outputs/roborazzi/login_screen_loading.png") {
            LoginScreen(
                state = LoginContract.UiState(
                    rememberedEmail = "user@example.com",
                    rememberEmail = true,
                    isLoading = true,
                ),
                onIntent = {},
            )
        }
    }
}
