package com.mediasage

import androidx.compose.ui.window.ComposeUIViewController
import com.mediasage.di.appModule
import com.mediasage.di.databaseModule
import com.mediasage.di.sharedModule
import com.mediasage.di.themeModule
import org.koin.core.context.startKoin

fun initKoin() {
    startKoin {
        modules(
            databaseModule,
            themeModule,
            userModule,
            sharedModule(
                serverBaseUrl = "https://media-sage-production.up.railway.app",
                supabaseUrl = "",
                supabaseAnonKey = ""
            ),
            appModule,
        )
    }
}

fun MainViewController(isDebugBuild: Boolean = false) = ComposeUIViewController { App(isDebugBuild = isDebugBuild) }