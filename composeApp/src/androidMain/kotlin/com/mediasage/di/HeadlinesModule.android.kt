package com.mediasage.di

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.mediasage.data.HeadlineCategoryPreferencesRepository
import okio.Path.Companion.toPath
import org.koin.dsl.module

val headlinesModule = module {
    single {
        HeadlineCategoryPreferencesRepository(
            PreferenceDataStoreFactory.createWithPath {
                val context: Context = get()
                context.filesDir.resolve(HeadlineCategoryPreferencesRepository.FILE_NAME).absolutePath.toPath()
            }
        )
    }
}
