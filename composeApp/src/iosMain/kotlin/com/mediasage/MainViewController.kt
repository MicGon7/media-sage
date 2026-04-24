package com.mediasage

import androidx.compose.ui.window.ComposeUIViewController
import com.mediasage.di.appModule
import com.mediasage.di.databaseModule
import com.mediasage.di.sharedModule
import org.koin.core.context.startKoin

fun initKoin(serverBaseUrl: String) {
    startKoin {
        modules(
            databaseModule,
            sharedModule(serverBaseUrl),
            appModule,
        )
    }
}

fun MainViewController() = ComposeUIViewController { App() }