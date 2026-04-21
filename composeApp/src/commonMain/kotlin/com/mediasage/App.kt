package com.mediasage

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.mediasage.navigation.MediaSageScaffold
import com.mediasage.theme.MediaSageTheme

@Composable
@Preview
fun App() {
    MediaSageTheme {
        MediaSageScaffold()
    }
}
