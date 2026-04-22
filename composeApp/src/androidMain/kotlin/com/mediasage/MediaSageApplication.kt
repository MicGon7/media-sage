package com.mediasage

import android.app.Application
import com.mediasage.di.appModule
import com.mediasage.di.databaseModule
import com.mediasage.di.sharedModule
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

class MediaSageApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidContext(this@MediaSageApplication)
            modules(databaseModule, sharedModule(BuildConfig.SERVER_BASE_URL), appModule)
        }
    }
}
