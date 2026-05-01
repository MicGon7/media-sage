package com.mediasage

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.mediasage.navigation.MediaSageScaffold
import com.mediasage.theme.MediaSageTheme
import org.koin.compose.viewmodel.koinViewModel

@Composable
@Preview
fun App() {
    koinViewModel<AppViewModel>()
    MediaSageTheme {
        MediaSageScaffold()
    }
}
