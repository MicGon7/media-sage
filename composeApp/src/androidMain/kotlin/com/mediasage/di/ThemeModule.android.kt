package com.mediasage.di

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.mediasage.data.ThemePreferencesRepository
import okio.Path.Companion.toPath
import org.koin.dsl.module

val themeModule = module {
    single {
        ThemePreferencesRepository(
            PreferenceDataStoreFactory.createWithPath {
                val context: Context = get()
                context.filesDir.resolve(ThemePreferencesRepository.FILE_NAME).absolutePath.toPath()
            }
        )
    }
}
