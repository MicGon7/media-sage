package com.mediasage.feature.smoketest

import android.app.Application
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import com.mediasage.theme.MediaSageTheme
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Proves the MS-581 render loop: a `commonMain` composable is rendered to a PNG on
 * the JVM via Robolectric — no emulator or connected device. This is not a
 * regression gate (no golden comparison); it exists so the worker can produce an
 * image of its UI, view it, and attach it to the PR.
 *
 * SDK is pinned below the compile SDK so the render does not require the JDK 21 /
 * Android 36 toolchain, keeping it portable to the Linux worker container.
 *
 * A bare [Application] is used instead of the app's `MediaSageApplication` so the render
 * does not boot Koin/DI — a UI render needs only the composable, not the app runtime.
 * Booting the real Application also throws `KoinApplicationAlreadyStartedException` on
 * the second test in the JVM.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = Application::class)
class SmokeTestScreenRenderTest {

    @Test
    fun rendersSmokeTestScreenLight() {
        captureRoboImage("build/outputs/roborazzi/smoke_test_screen_light.png") {
            MediaSageTheme(darkTheme = false) {
                SmokeTestScreen()
            }
        }
    }

    // A second capture in the same test class proves the multi-screen pattern: each
    // captureRoboImage call yields its own PNG, so a ticket touching a main screen and
    // a detail screen simply adds one block per screen and recordRoborazziDebug emits
    // one image each.
    @Test
    fun rendersSmokeTestScreenDark() {
        captureRoboImage("build/outputs/roborazzi/smoke_test_screen_dark.png") {
            MediaSageTheme(darkTheme = true) {
                SmokeTestScreen()
            }
        }
    }
}
