package com.mediasage

import androidx.compose.ui.window.ComposeUIViewController
import com.mediasage.di.appModule
import com.mediasage.di.databaseModule
import com.mediasage.di.sharedModule
import com.mediasage.di.themeModule
import com.mediasage.di.userModule
import org.koin.core.context.startKoin

fun initKoin(supabaseUrl: String, supabaseAnonKey: String) {
    startKoin {
        modules(
            databaseModule,
            themeModule,
            userModule,
            sharedModule(
                serverBaseUrl = "https://media-sage-production.up.railway.app",
                supabaseUrl = supabaseUrl,
                supabaseAnonKey = supabaseAnonKey
            ),
            appModule,
        )
    }
}

fun MainViewController(isDebugBuild: Boolean = false, appVersion: String = "") =
    ComposeUIViewController { App(isDebugBuild = isDebugBuild, appVersion = appVersion) }