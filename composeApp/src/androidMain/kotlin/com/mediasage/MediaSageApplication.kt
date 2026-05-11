package com.mediasage

import android.app.Application
import com.mediasage.di.MockConfig
import com.mediasage.di.appModule
import com.mediasage.di.databaseModule
import com.mediasage.di.mockApiModule
import com.mediasage.di.sharedModule
import com.mediasage.di.themeModule
import com.mediasage.di.userModule
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

class MediaSageApplication : Application() {

    override fun onCreate() {
        super.onCreate()

        val modules = buildList {
            add(databaseModule)
            add(themeModule)
            add(userModule)
            add(sharedModule(BuildConfig.SERVER_BASE_URL, BuildConfig.SUPABASE_URL, BuildConfig.SUPABASE_ANON_KEY))
            add(appModule)
            if (BuildConfig.USE_MOCK_DATA) add(mockApiModule)
        }

        startKoin {
            androidContext(this@MediaSageApplication)
            modules(modules)
        }
    }
}
