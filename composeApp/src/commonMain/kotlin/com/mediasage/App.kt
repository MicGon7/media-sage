package com.mediasage

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.tooling.preview.Preview
import com.mediasage.navigation.MediaSageScaffold
import com.mediasage.theme.MediaSageTheme
import org.koin.compose.viewmodel.koinViewModel

@Composable
@Preview
fun App(isDebugBuild: Boolean = false) {
    val appViewModel = koinViewModel<AppViewModel>()
    val darkMode by appViewModel.darkMode.collectAsState()
    CompositionLocalProvider(LocalIsDebugBuild provides isDebugBuild) {
        MediaSageTheme(darkTheme = darkMode) {
            MediaSageScaffold()
        }
    }
}
