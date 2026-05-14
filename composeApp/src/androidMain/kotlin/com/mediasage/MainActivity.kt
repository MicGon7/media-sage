package com.mediasage

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.tooling.preview.Preview
import com.mediasage.theme.AppTheme
import org.koin.compose.viewmodel.koinViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            val appViewModel = koinViewModel<AppViewModel>()
            val darkMode by appViewModel.darkMode.collectAsState()
            val appTheme by appViewModel.appTheme.collectAsState()

            DisposableEffect(darkMode, appTheme) {
                // Modern is always dark navy; all bars are transparent so the app background shows through
                val useDarkBars = darkMode == true || appTheme == AppTheme.MODERN
                val barStyle = if (useDarkBars) {
                    SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
                } else {
                    SystemBarStyle.light(
                        android.graphics.Color.TRANSPARENT,
                        android.graphics.Color.TRANSPARENT,
                    )
                }
                enableEdgeToEdge(
                    statusBarStyle = barStyle,
                    navigationBarStyle = barStyle,
                )
                onDispose {}
            }

            App(isDebugBuild = BuildConfig.DEBUG)
        }
    }
}

@Preview
@Composable
fun AppAndroidPreview() {
    App()
}
