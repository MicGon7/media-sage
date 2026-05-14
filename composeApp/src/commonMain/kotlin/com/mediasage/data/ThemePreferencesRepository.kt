package com.mediasage.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.mediasage.theme.AppTheme
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class ThemePreferencesRepository(private val dataStore: DataStore<Preferences>) {

    val darkMode: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[DARK_MODE_KEY] ?: false
    }

    suspend fun setDarkMode(enabled: Boolean) {
        dataStore.edit { it[DARK_MODE_KEY] = enabled }
    }

    val appTheme: Flow<AppTheme> = dataStore.data.map { prefs ->
        AppTheme.entries.firstOrNull { it.name == prefs[APP_THEME_KEY] } ?: AppTheme.CLASSIC
    }

    suspend fun setAppTheme(theme: AppTheme) {
        dataStore.edit { it[APP_THEME_KEY] = theme.name }
    }

    companion object {
        private val DARK_MODE_KEY = booleanPreferencesKey("dark_mode")
        private val APP_THEME_KEY = stringPreferencesKey("app_theme")
        const val FILE_NAME = "theme.preferences_pb"
    }
}
