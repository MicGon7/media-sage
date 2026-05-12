package com.mediasage.di

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.mediasage.data.UserPreferencesRepository
import okio.Path.Companion.toPath
import org.koin.dsl.module

val userModule = module {
    single {
        UserPreferencesRepository(
            PreferenceDataStoreFactory.createWithPath {
                val context: Context = get()
                context.filesDir.resolve(UserPreferencesRepository.FILE_NAME).absolutePath.toPath()
            }
        )
    }
}
