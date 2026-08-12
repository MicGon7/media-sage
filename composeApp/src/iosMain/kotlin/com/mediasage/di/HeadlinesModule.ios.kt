package com.mediasage.di

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.mediasage.data.HeadlineCategoryPreferencesRepository
import kotlinx.cinterop.ExperimentalForeignApi
import okio.Path.Companion.toPath
import org.koin.dsl.module
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSUserDomainMask

@OptIn(ExperimentalForeignApi::class)
val headlinesModule = module {
    single {
        HeadlineCategoryPreferencesRepository(
            PreferenceDataStoreFactory.createWithPath {
                val docDir = NSFileManager.defaultManager.URLForDirectory(
                    directory = NSDocumentDirectory,
                    inDomain = NSUserDomainMask,
                    appropriateForURL = null,
                    create = false,
                    error = null
                )
                (requireNotNull(docDir).path + "/${HeadlineCategoryPreferencesRepository.FILE_NAME}").toPath()
            }
        )
    }
}
