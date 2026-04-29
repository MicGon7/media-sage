package com.mediasage

import androidx.compose.ui.window.ComposeUIViewController
import com.mediasage.di.appModule
import com.mediasage.di.databaseModule
import com.mediasage.di.sharedModule
import org.koin.core.context.startKoin

fun initKoin() {
    startKoin {
        modules(
            databaseModule,
            sharedModule("https://media-sage-production.up.railway.app"),
            appModule,
        )
    }
}

fun MainViewController() = ComposeUIViewController { App() }