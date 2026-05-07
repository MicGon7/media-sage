package com.mediasage.di

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.mediasage.data.ThemePreferencesRepository
import okio.Path.Companion.toPath
import org.koin.dsl.module
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSUserDomainMask

@OptIn(ExperimentalForeignApi::class)
val themeModule = module {
    single {
        ThemePreferencesRepository(
            PreferenceDataStoreFactory.createWithPath {
                val docDir = NSFileManager.defaultManager.URLForDirectory(
                    directory = NSDocumentDirectory,
                    inDomain = NSUserDomainMask,
                    appropriateForURL = null,
                    create = false,
                    error = null
                )
                (requireNotNull(docDir).path + "/${ThemePreferencesRepository.FILE_NAME}").toPath()
            }
        )
    }
}
