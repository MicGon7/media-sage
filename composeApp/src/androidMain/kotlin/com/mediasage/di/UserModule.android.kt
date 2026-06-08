package com.mediasage.di

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.mediasage.data.AuthPreferencesRepository
import okio.Path.Companion.toPath
import org.koin.dsl.module

val userModule = module {
    single {
        AuthPreferencesRepository(
            PreferenceDataStoreFactory.createWithPath {
                val context: Context = get()
                context.filesDir.resolve(AuthPreferencesRepository.FILE_NAME).absolutePath.toPath()
            }
        )
    }
}
